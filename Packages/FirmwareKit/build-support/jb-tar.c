// The tar firmwarekit's jailbreak step shells out to, for Android, where /usr/bin/tar does not exist.
//
// Why this exists. SystemEdits.Cydia (Packages/FirmwareKit/Sources/FirmwareKit/SystemEdits/Cydia.swift,
// the same file in the firmwarekit the APK reuses) installs the Cydia bootstrap by running the host's
// tar three times per archive:
//
//   tar -tf  <archive>                                   the member names, to know what an extraction adds
//   tar -tvf <archive>                                   the same with modes, for the set-user-ID files
//   tar -xkf <archive> -C <volume> --exclude <path>      the extraction itself, keeping what is there
//
// On macOS that is bsdtar and on Linux GNU tar, and neither exists on Android: /usr/bin has nothing in
// it, and toybox's tar (Android's own) has neither -k nor --exclude. So the step failed on every phone
// with "tar -tf: ...", which is the failure this tool removes. It is small on purpose -- it reads the
// three archives the recipe is pinned to (Legacy-iOS-Kit's freeze.tar.gz, cydiasubstrate.tar and
// cydiahttpatch.tar: ustar, gzip or not, directories, files, symbolic links, one hard link, five
// set-user-ID files, no PAX or GNU long names) -- and it is deliberately not a general tar.
//
// Where it differs from GNU tar, and why. GNU tar's -k treats an entry the volume already has as an
// error: it prints "Cannot open: File exists" and exits 2. The recipe extracts a stock iOS root, where
// /usr/bin/login, /bin/su and Cydia's own cydo are already present, so that behaviour would fail every
// jailbreak. Here an existing entry is kept -- which is what -k means and what the recipe's own comment
// says it gets -- and the run exits 0; the skips are logged to stderr. The one thing the recipe reads
// out of the listings is -tv: its parser expects the bsdtar/Mac line
//
//   -rwsr-sr-x 0 root wheel 133808 Feb 16  2017 ./usr/libexec/cydia/cydo
//
// (mode, links, user, group, size, month, day, year, path -- nine whitespace-separated fields), so that
// is the format printed here, with the user and group as two fields; GNU tar's "root/wheel" and ISO date
// make six and the recipe then finds no set-user-ID file at all.
//
// Build (the workflow does this): aarch64-linux-android29-clang -O2 -lz, or the host's cc for the tests.
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#include <utime.h>
#include <zlib.h>

#define BLOCK 512
#define NAME_MAX_TAR 4096   /* the longest name this accepts after ./ and PAX/GNU long-name handling */

static const char *program = "tar";

static void fail(const char *format, ...) {
    va_list arguments;
    va_start(arguments, format);
    fprintf(stderr, "%s: ", program);
    vfprintf(stderr, format, arguments);
    va_end(arguments);
    fputc('\n', stderr);
}

/* --- reading: a plain tar or a gzip stream, 512 bytes at a time ------------------------------------ */

struct input {
    const char *path;
    FILE *file;
    z_stream gzip;
    bool compressed;
    unsigned char in[64 * 1024];
};

static bool input_open(struct input *in, const char *path) {
    memset(in, 0, sizeof *in);
    in->path = path;
    in->file = fopen(path, "rb");
    if (in->file == NULL) {
        fail("%s: %s", path, strerror(errno));
        return false;
    }
    unsigned char magic[2] = {0, 0};
    if (fread(magic, 1, 2, in->file) != 2 && ferror(in->file)) {
        fail("%s: %s", path, strerror(errno));
        return false;
    }
    rewind(in->file);
    in->compressed = magic[0] == 0x1f && magic[1] == 0x8b;
    if (in->compressed && inflateInit2(&in->gzip, 15 + 16) != Z_OK) {   /* 16: gzip only */
        fail("%s: cannot start gzip", path);
        return false;
    }
    return true;
}

static void input_close(struct input *in) {
    if (in->compressed) inflateEnd(&in->gzip);
    if (in->file != NULL) fclose(in->file);
}

/** Exactly `size` bytes of the archive's contents, or false at a short read. */
static bool input_read(struct input *in, unsigned char *into, size_t size) {
    if (!in->compressed) {
        if (fread(into, 1, size, in->file) != size) {
            fail("%s: unexpected end of archive", in->path);
            return false;
        }
        return true;
    }
    in->gzip.next_out = into;
    in->gzip.avail_out = (uInt) size;
    while (in->gzip.avail_out > 0) {
        if (in->gzip.avail_in == 0) {
            size_t got = fread(in->in, 1, sizeof in->in, in->file);
            if (got == 0) {
                fail("%s: unexpected end of gzip stream", in->path);
                return false;
            }
            in->gzip.next_in = in->in;
            in->gzip.avail_in = (uInt) got;
        }
        int status = inflate(&in->gzip, Z_NO_FLUSH);
        if (status != Z_OK && status != Z_STREAM_END) {
            fail("%s: bad gzip stream (%s)", in->path, in->gzip.msg ? in->gzip.msg : "?");
            return false;
        }
        if (status == Z_STREAM_END && in->gzip.avail_out > 0) {
            fail("%s: unexpected end of gzip stream", in->path);
            return false;
        }
    }
    return true;
}

/** Skip `size` bytes: a tar member's payload and its padding to the next block. */
static bool input_skip(struct input *in, uint64_t size) {
    unsigned char scratch[64 * 1024];
    while (size > 0) {
        size_t chunk = size < sizeof scratch ? (size_t) size : sizeof scratch;
        if (!input_read(in, scratch, chunk)) return false;
        size -= chunk;
    }
    return true;
}

/* --- one member ---------------------------------------------------------------------------------- */

struct entry {
    char name[NAME_MAX_TAR];
    char link[NAME_MAX_TAR];
    char uname[32];
    char gname[32];
    uint64_t size;
    uint32_t mode;
    int64_t mtime;
    char type;          /* '0' file, '5' directory, '2' symlink, '1' hard link */
};

static uint64_t octal(const unsigned char *field, size_t size) {
    /* Base-256 (the high bit set) is how tar writes values an octal field cannot hold. */
    if (size > 0 && (field[0] & 0x80)) {
        uint64_t value = (uint64_t) (field[0] & 0x7f);
        for (size_t i = 1; i < size; i++) value = (value << 8) | field[i];
        return value;
    }
    uint64_t value = 0;
    for (size_t i = 0; i < size; i++) {
        unsigned char c = field[i];
        if (c == ' ' || c == '\0') {
            if (value != 0) break;
            continue;
        }
        if (c < '0' || c > '7') break;
        value = (value << 3) | (uint64_t) (c - '0');
    }
    return value;
}

static void text(char *into, size_t size, const unsigned char *field, size_t length) {
    size_t n = 0;
    while (n < length && field[n] != '\0' && n + 1 < size) {
        into[n] = (char) field[n];
        n++;
    }
    into[n] = '\0';
}

/** A header's checksum: its own field read as spaces, everything else as unsigned bytes. */
static bool checksum_holds(const unsigned char block[BLOCK]) {
    unsigned long sum = 0, want;
    for (size_t i = 0; i < BLOCK; i++) sum += (i >= 148 && i < 156) ? ' ' : block[i];
    unsigned char field[8];
    memcpy(field, block + 148, sizeof field);
    field[0] &= 0x7f;   /* some writers set the high bit of the first byte */
    want = (unsigned long) octal(field, sizeof field);
    return sum == want;
}

/**
 * A header read into `e`, or false when the archive ended. PAX ('x'/'g') and GNU long-name ('L'/'K')
 * headers are consumed here and applied to the member that follows, so a caller only ever sees entries.
 * A checksum that does not hold is an error: silently taking a damaged name would extract to the wrong
 * place, and the archives this reads are pinned by SHA-1 for exactly that reason.
 */
static bool next_entry(struct input *in, struct entry *e, bool *ended, char **pending_name, char **pending_link) {
    unsigned char block[BLOCK];
    for (;;) {
        if (!input_read(in, block, BLOCK)) return false;
        bool zero = true;
        for (size_t i = 0; i < BLOCK && zero; i++) zero = block[i] == 0;
        if (zero) {
            *ended = true;
            return true;
        }
        if (!checksum_holds(block)) {
            fail("%s: bad header checksum", in->path);
            return false;
        }
        char type = (char) block[156];
        uint64_t size = octal(block + 124, 12);
        if (type == 'x' || type == 'g' || type == 'L' || type == 'K') {
            if (size == 0 || size > NAME_MAX_TAR) {
                fail("%s: unusable long-name header", in->path);
                return false;
            }
            char *text_block = malloc((size_t) size + 1);
            if (text_block == NULL) {
                fail("out of memory");
                return false;
            }
            if (!input_read(in, (unsigned char *) text_block, (size_t) size)) {
                free(text_block);
                return false;
            }
            size_t used = (size_t) size;
            while (used > 0 && text_block[used - 1] == '\0') used--;
            text_block[used] = '\0';
            if (size % BLOCK != 0 && !input_skip(in, BLOCK - size % BLOCK)) {
                free(text_block);
                return false;
            }
            bool transferred = false;
            if (type == 'L' || type == 'K') {
                char **slot = type == 'L' ? pending_name : pending_link;
                free(*slot);
                *slot = text_block;   /* the member after this one takes it, and frees it there */
                transferred = true;
            } else if (type == 'x') {
                /* "len key=value\n": the fields that change where or what the member is. */
                for (char *at = text_block; at != NULL && *at != '\0';) {
                    char *newline = strchr(at, '\n');
                    if (newline == NULL) break;
                    *newline = '\0';
                    char *value = strchr(at, '=');
                    if (value != NULL) {
                        *value++ = '\0';
                        const char *key = at + strspn(at, "0123456789 ");   /* the record's length */
                        char **slot = strcmp(key, "path") == 0      ? pending_name
                                      : strcmp(key, "linkpath") == 0 ? pending_link
                                                                     : NULL;
                        if (slot != NULL) {
                            free(*slot);
                            *slot = strdup(value);
                            if (*slot == NULL) {
                                fail("out of memory");
                                free(text_block);
                                return false;
                            }
                        }
                    }
                    at = newline + 1;
                }
            }
            if (!transferred) free(text_block);
            continue;
        }

        memset(e, 0, sizeof *e);
        char name[256], prefix[156];
        text(name, sizeof name, block, 100);
        text(prefix, sizeof prefix, block + 345, 155);
        bool posix = memcmp(block + 257, "ustar\0", 6) == 0;   /* "ustar  " is GNU's, which has no prefix */
        if (posix && prefix[0] != '\0')
            snprintf(e->name, sizeof e->name, "%s/%s", prefix, name);
        else
            snprintf(e->name, sizeof e->name, "%s", name);
        if (*pending_name != NULL) {
            snprintf(e->name, sizeof e->name, "%s", *pending_name);
            free(*pending_name);
            *pending_name = NULL;
        }
        text(e->link, sizeof e->link, block + 157, 100);
        if (*pending_link != NULL) {
            snprintf(e->link, sizeof e->link, "%s", *pending_link);
            free(*pending_link);
            *pending_link = NULL;
        }
        text(e->uname, sizeof e->uname, block + 265, 32);
        text(e->gname, sizeof e->gname, block + 297, 32);
        e->mode = (uint32_t) octal(block + 100, 8);
        e->size = size;
        e->mtime = (int64_t) octal(block + 136, 12);
        e->type = (type == '\0') ? '0' : type;
        return true;
    }
}

/* --- what the caller asked for ------------------------------------------------------------------- */

struct options {
    bool list, verbose, extract, keep;
    const char *archive;
    const char *directory;
    const char *excludes[16];
    size_t excludes_count;
};

/** A member path as a path under the destination: no leading ./, no trailing /, nothing above it. */
static bool relative_path(const char *name, char *into, size_t size) {
    const char *at = name;
    while (at[0] == '.' && at[1] == '/') at += 2;
    while (at[0] == '/') at++;
    size_t n = strlen(at);
    while (n > 0 && at[n - 1] == '/') n--;
    if (n == 0) {
        /* "./" and "." are the destination itself, which every one of these archives starts with. */
        if (size < 2) return false;
        into[0] = '.';
        into[1] = '\0';
        return true;
    }
    if (n + 1 > size) {
        fail("%s: path too long", name);
        return false;
    }
    memcpy(into, at, n);
    into[n] = '\0';
    if (strstr(into, "../") != NULL || strcmp(into, "..") == 0 || into[0] == '/') {
        fail("%s: refuses to extract outside the destination", name);
        return false;
    }
    return true;
}

static bool excluded(const struct options *o, const char *name) {
    char clean[NAME_MAX_TAR];
    if (!relative_path(name, clean, sizeof clean)) return false;
    for (size_t i = 0; i < o->excludes_count; i++) {
        char pattern[NAME_MAX_TAR];
        if (!relative_path(o->excludes[i], pattern, sizeof pattern)) continue;
        size_t n = strlen(pattern);
        if (strcmp(clean, pattern) == 0) return true;
        if (strncmp(clean, pattern, n) == 0 && clean[n] == '/') return true;
    }
    return false;
}

static void mode_string(uint32_t mode, char type, char into[11]) {
    static const char *rwx = "rwxrwxrwx";
    into[0] = type == '5' ? 'd' : type == '2' ? 'l' : type == '1' ? 'h' : '-';
    for (int i = 0; i < 9; i++) into[i + 1] = (mode & (1u << (8 - i))) ? rwx[i] : '-';
    if (mode & 04000) into[3] = (mode & 0100) ? 's' : 'S';
    if (mode & 02000) into[6] = (mode & 0010) ? 's' : 'S';
    if (mode & 01000) into[9] = (mode & 0001) ? 't' : 'T';
    into[10] = '\0';
}

/** The listing line the recipe's parser reads: nine fields, user and group apart, so see the note above. */
static void print_verbose(const struct entry *e) {
    static const char *months[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                   "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    char mode[11];
    time_t when = (time_t) e->mtime;
    struct tm broken;
    gmtime_r(&when, &broken);
    const char *month = (broken.tm_mon >= 0 && broken.tm_mon < 12) ? months[broken.tm_mon] : "Jan";
    char user[32], group[32];
    snprintf(user, sizeof user, "%s", e->uname[0] != '\0' ? e->uname : "0");
    snprintf(group, sizeof group, "%s", e->gname[0] != '\0' ? e->gname : "0");
    mode_string(e->mode, e->type, mode);
    if (e->type == '2')
        printf("%s 0 %s %s %llu %s %2d  %04d %s -> %s\n", mode, user, group,
               (unsigned long long) e->size, month, broken.tm_mday, broken.tm_year + 1900, e->name, e->link);
    else
        printf("%s 0 %s %s %llu %s %2d  %04d %s\n", mode, user, group, (unsigned long long) e->size, month,
               broken.tm_mday, broken.tm_year + 1900, e->name);
}

/** Every directory above `path` under `directory`, created 0755 where missing. */
static bool make_parents(const char *directory, const char *path, char *full, size_t size) {
    int written = snprintf(full, size, "%s/%s", directory, path);
    if (written <= 0 || (size_t) written >= size) {
        fail("%s: path too long", path);
        return false;
    }
    for (char *at = full + strlen(directory) + 1; *at != '\0'; at++) {
        if (*at != '/') continue;
        *at = '\0';
        if (mkdir(full, 0755) != 0 && errno != EEXIST) {
            fail("%s: %s", full, strerror(errno));
            return false;
        }
        *at = '/';
    }
    return true;
}

static bool exists(const char *path) {
    struct stat status;
    return lstat(path, &status) == 0;
}

/** The extraction of one member, GNU tar's -k as the recipe means it: what is there is kept, quietly. */
static bool extract_member(const struct options *o, struct input *in, const struct entry *e) {
    char path[NAME_MAX_TAR], full[NAME_MAX_TAR + 512];
    if (!relative_path(e->name, path, sizeof path)) return false;
    if (excluded(o, e->name)) return input_skip(in, (e->size + BLOCK - 1) / BLOCK * BLOCK);
    if (!make_parents(o->directory, path, full, sizeof full)) return false;

    switch (e->type) {
    case '5': {
        /* An existing directory keeps its mode (the volume's own, under -k); a created one gets the
         * archive's, chmod'ed after mkdir because the umask took bits off it. */
        if (mkdir(full, e->mode & 07777) == 0) {
            if (chmod(full, e->mode & 07777) != 0 && errno != EPERM) {
                fail("%s: cannot set mode: %s", full, strerror(errno));
                return false;
            }
        } else if (errno != EEXIST) {
            fail("%s: %s", full, strerror(errno));
            return false;
        }
        break;
    }
    case '2': {
        if (exists(full)) {
            fprintf(stderr, "%s: %s: exists, kept\n", program, path);
            break;
        }
        if (symlink(e->link, full) != 0) {
            fail("%s: %s", full, strerror(errno));
            return false;
        }
        break;
    }
    case '1': {
        char target[NAME_MAX_TAR], target_full[NAME_MAX_TAR + 512];
        if (!relative_path(e->link, target, sizeof target)) return false;
        if (exists(full)) {
            fprintf(stderr, "%s: %s: exists, kept\n", program, path);
            break;
        }
        if (snprintf(target_full, sizeof target_full, "%s/%s", o->directory, target) >= (int) sizeof target_full) {
            fail("%s: link target too long", path);
            return false;
        }
        if (link(target_full, full) != 0) {
            fail("%s: cannot link to %s: %s", path, target, strerror(errno));
            return false;
        }
        break;
    }
    case '0':
    case '7': {
        if (exists(full)) {
            fprintf(stderr, "%s: %s: exists, kept\n", program, path);
            break;
        }
        int fd = open(full, O_WRONLY | O_CREAT | O_EXCL, (mode_t) (e->mode & 07777));
        if (fd < 0) {
            fail("%s: %s", full, strerror(errno));
            return false;
        }
        bool ok = true;
        uint64_t left = e->size;
        unsigned char buffer[64 * 1024];
        while (left > 0 && ok) {
            size_t chunk = left < sizeof buffer ? (size_t) left : sizeof buffer;
            if (!input_read(in, buffer, chunk)) ok = false;
            else if (write(fd, buffer, chunk) != (ssize_t) chunk) {
                fail("%s: %s", full, strerror(errno));
                ok = false;
            }
            left -= chunk;
        }
        close(fd);
        if (!ok) return false;
        if (chmod(full, (mode_t) (e->mode & 07777)) != 0 && errno != EPERM) {
            fail("%s: cannot set mode: %s", full, strerror(errno));
            return false;
        }
        struct timespec times[2] = {{e->mtime, 0}, {e->mtime, 0}};
        if (utimensat(AT_FDCWD, full, times, 0) != 0 && errno != EPERM)
            fprintf(stderr, "%s: %s: cannot set time: %s\n", program, path, strerror(errno));
        if (e->size % BLOCK != 0 && !input_skip(in, BLOCK - e->size % BLOCK)) return false;
        return true;
    }
    default:
        fail("%s: entry type '%c' is not in these archives", path, e->type);
        return false;
    }
    return input_skip(in, (e->size + BLOCK - 1) / BLOCK * BLOCK);
}

static bool parse_options(int argc, char **argv, struct options *o) {
    memset(o, 0, sizeof *o);
    for (int i = 1; i < argc; i++) {
        const char *argument = argv[i];
        if (strcmp(argument, "--exclude") == 0 || strncmp(argument, "--exclude=", 10) == 0) {
            const char *value = argument[9] == '=' ? argument + 10 : (i + 1 < argc ? argv[++i] : NULL);
            if (value == NULL) {
                fail("--exclude needs a pattern");
                return false;
            }
            if (o->excludes_count == sizeof o->excludes / sizeof o->excludes[0]) {
                fail("too many --exclude patterns");
                return false;
            }
            o->excludes[o->excludes_count++] = value;
            continue;
        }
        if (argument[0] != '-' || argument[1] == '\0') {
            fail("%s: unexpected argument", argument);
            return false;
        }
        for (const char *flag = argument + 1; *flag != '\0'; flag++) {
            bool ends_here = false;
            switch (*flag) {
            case 't': o->list = true; break;
            case 'v': o->verbose = true; break;
            case 'x': o->extract = true; break;
            case 'k': o->keep = true; break;
            case 'z': case 'j': case 'J': break;   /* the stream says what it is */
            case 'f': case 'C': {
                const char *value = flag[1] != '\0' ? flag + 1 : (i + 1 < argc ? argv[++i] : NULL);
                if (value == NULL) {
                    fail("-%c needs an argument", *flag);
                    return false;
                }
                if (*flag == 'f') o->archive = value;
                else o->directory = value;
                ends_here = true;   /* -fFILE or -C DIR: this argument is spent */
                break;
            }
            case 'h': case 'o': case 'm': case 'p': case 'O': break;   /* accepted, not needed here */
            default:
                fail("-%c is not supported", *flag);
                return false;
            }
            if (ends_here) break;
        }
    }
    if (o->archive == NULL || (!o->list && !o->extract)) {
        fail("usage: tar -t[f FILE] | tar -x[f FILE] [-C DIR] [-k] [--exclude PATTERN]");
        return false;
    }
    if (o->directory == NULL) o->directory = ".";
    return true;
}

int main(int argc, char **argv) {
    struct options options;
    if (!parse_options(argc, argv, &options)) return 1;

    struct input in;
    if (!input_open(&in, options.archive)) return 1;
    char *pending_name = NULL, *pending_link = NULL;
    bool ok = true, ended = false;
    while (ok && !ended) {
        struct entry entry;
        if (!next_entry(&in, &entry, &ended, &pending_name, &pending_link)) {
            ok = false;
            break;
        }
        if (ended) break;
        if (options.list) {
            if (options.verbose) print_verbose(&entry);
            else printf("%s\n", entry.name);
            ok = input_skip(&in, (entry.size + BLOCK - 1) / BLOCK * BLOCK);
            fflush(stdout);
            continue;
        }
        ok = extract_member(&options, &in, &entry);
    }
    free(pending_name);
    free(pending_link);
    input_close(&in);
    if (!ok) return 1;
    if (fflush(stdout) != 0) {
        fail("cannot write the listing: %s", strerror(errno));
        return 1;
    }
    return 0;
}
