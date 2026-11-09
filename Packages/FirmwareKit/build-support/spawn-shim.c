// A preloaded shim for the firmwarekit process, which is where the iPad 1 (k48) recipe patches iBoot.
//
// Two things about that recipe only work on macOS, and the shim repairs both at the point they happen rather than
// guessing from outside the process:
//
//  1. The patcher is looked up as "<directory>/iBoot32Patcher" -- no "lib" prefix and no ".so" suffix -- because
//     on macOS the tool ships in the app bundle next to the executable. An Android APK cannot carry that name
//     (its native libraries must be lib*.so to be packaged and extracted), so access()/stat()/open() for that
//     name are redirected to the copy the APK does carry (libiBoot32Patcher.so), and so are the spawn calls that
//     the recipe then makes with the name it was given.
//  2. The patcher is launched as "env <patcher> <in> <out> --rsa ...". Foundation finds a bare name on PATH on
//     macOS (Darwin spawns through posix_spawnp) and /usr/bin/env exists there anyway; on Android bionic's
//     posix_spawn searches nothing and /usr/bin does not exist, so a spawn of env(1) runs the program env was
//     given, with env's arguments, directly.
//  3. The jailbreak option (firmwarekit create --jailbreak) installs the Cydia bootstrap by running the host's
//     tar: "-tf", "-tvf" and "-xkf ... -C ... --exclude ..." (SystemEdits/Cydia.swift). /usr/bin has no tar on
//     Android, and toybox's has neither -k nor --exclude, so those spawns are pointed at libjbTar.so, the
//     small tar this APK builds from build-support/jb-tar.c. It is run without this shim in front of it
//     (LD_PRELOAD is dropped for that one spawn: the tool needs nothing redirected, and its own file accesses
//     would otherwise fill this log).
//
// Everything it sees is written to SPAWN_SHIM_LOG (the app names it), and the app appends the tail of that file to
// a preparation failure, so the log says which path was wanted and what the process did with it.
//
// Only libc and dlopen: this is loaded into a statically linked Swift binary and must not depend on anything the
// APK does not already carry.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <spawn.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define SHIM_LOG_LIMIT 400
#define PATCHER_NAME "iBoot32Patcher"
#define PATCHER_REAL "libiBoot32Patcher.so"
#define TAR_PATH "/usr/bin/tar"
#define TAR_REAL "libjbTar.so"
#define TAR_ENV "FIRMWAREKIT_TAR"

static int shim_fd = -2;
static int shim_lines = 0;
static char shim_dir[PATH_MAX];
static int shim_quiet = 0;

static void shim_log(const char *format, ...) {
    if (shim_quiet || shim_lines >= SHIM_LOG_LIMIT) return;
    if (shim_fd == -2) {
        const char *path = getenv("SPAWN_SHIM_LOG");
        shim_fd = path && *path ? open(path, O_WRONLY | O_CREAT | O_APPEND, 0644) : -1;
    }
    if (shim_fd < 0) return;
    char line[400];
    va_list arguments;
    va_start(arguments, format);
    int n = vsnprintf(line, sizeof line, format, arguments);
    va_end(arguments);
    if (n > 0) {
        shim_lines++;
        ssize_t ignored = write(shim_fd, line, (size_t) (n < (int) sizeof line ? n : (int) sizeof line));
        (void) ignored;
    }
}

__attribute__((constructor)) static void shim_loaded(void) {
    Dl_info info;
    if (dladdr((void *) shim_loaded, &info) != 0 && info.dli_fname != NULL) {
        snprintf(shim_dir, sizeof shim_dir, "%s", info.dli_fname);
        char *slash = strrchr(shim_dir, '/');
        if (slash != NULL) *slash = '\0';
    }
    // The one process this shim is *not* for is the tar it points firmwarekit at: preloaded into it by
    // inheritance, it would only log the extracted members' own accesses over the messages a failure needs.
    char exe[PATH_MAX];
    ssize_t got = readlink("/proc/self/exe", exe, sizeof exe - 1);
    if (got > 0) {
        exe[got] = '\0';
        const char *base = strrchr(exe, '/');
        base = base ? base + 1 : exe;
        if (strcmp(base, TAR_REAL) == 0) shim_quiet = 1;
    }
    if (shim_quiet) return;
    char cwd[PATH_MAX];
    shim_log("spawn-shim: loaded into pid %d cwd=%s TMPDIR=%s HOME=%s dir=%s\n", (int) getpid(),
             getcwd(cwd, sizeof cwd) ? cwd : "?", getenv("TMPDIR") ? getenv("TMPDIR") : "(unset)",
             getenv("HOME") ? getenv("HOME") : "(unset)", shim_dir[0] ? shim_dir : "(unknown)");
}

/**
 * The path firmwarekit asked for, as the path that exists here.
 *
 * The recipe tests what it is about to run before it runs it, so both macOS-only paths have to answer for the
 * checks as well as for the spawn: the bundle-style patcher name becomes the library the APK ships beside this
 * shim, and env(1) -- which the recipe spells "/usr/bin/env", a directory Android does not have -- becomes
 * toybox's, in /system/bin. Anything else is returned unchanged.
 */
static const char *redirected(const char *path, char *buffer, size_t size) {
    if (path == NULL) return path;
    if (strcmp(path, "env") == 0 || strcmp(path, "/usr/bin/env") == 0 || strcmp(path, "/bin/env") == 0) {
        snprintf(buffer, size, "/system/bin/env");
        shim_log("spawn-shim: %s -> %s\n", path, buffer);
        return buffer;
    }
    if (strcmp(path, TAR_PATH) == 0 || strcmp(path, "/bin/tar") == 0 || strcmp(path, "tar") == 0) {
        // The app names the copy it staged when it has one; otherwise it is the library beside this shim.
        const char *named = getenv(TAR_ENV);
        if (named != NULL && *named != '\0') snprintf(buffer, size, "%s", named);
        else if (shim_dir[0] != '\0') snprintf(buffer, size, "%s/%s", shim_dir, TAR_REAL);
        else return path;
        shim_log("spawn-shim: %s -> %s\n", path, buffer);
        return buffer;
    }
    if (shim_dir[0] == '\0') return path;
    const char *base = strrchr(path, '/');
    base = base ? base + 1 : path;
    if (strcmp(base, PATCHER_NAME) != 0) return path;
    snprintf(buffer, size, "%s/%s", shim_dir, PATCHER_REAL);
    shim_log("spawn-shim: %s -> %s\n", path, buffer);
    return buffer;
}

/** Whether a resolved path is the tar this shim points firmwarekit at. */
static int names_tar_binary(const char *path) {
    if (path == NULL) return 0;
    const char *base = strrchr(path, '/');
    base = base ? base + 1 : path;
    return strcmp(base, TAR_REAL) == 0;
}

/**
 * `envp` without LD_PRELOAD, or NULL when there is nothing to strip. This shim is preloaded into firmwarekit
 * by the app; the tar it spawns inherits that and would rewrite nothing but would log every member it
 * extracts. It is a copy of the pointers, never of the strings: only the array is this function's to free.
 */
static char **without_preload(char *const envp[], char ***allocated) {
    *allocated = NULL;
    if (envp == NULL) return NULL;
    size_t count = 0, kept = 0;
    while (envp[count] != NULL) {
        if (strncmp(envp[count], "LD_PRELOAD=", 11) != 0) kept++;
        count++;
    }
    if (kept == count) return NULL;
    char **copy = malloc((kept + 1) * sizeof *copy);
    if (copy == NULL) return NULL;   /* inheriting it is better than failing the spawn */
    size_t at = 0;
    for (size_t i = 0; i < count; i++)
        if (strncmp(envp[i], "LD_PRELOAD=", 11) != 0) copy[at++] = envp[i];
    copy[at] = NULL;
    *allocated = copy;
    return copy;
}

static int names_env(const char *path) {
    if (path == NULL) return 0;
    return strcmp(path, "env") == 0 || strcmp(path, "/usr/bin/env") == 0 ||
           strcmp(path, "/bin/env") == 0 || strcmp(path, "/system/bin/env") == 0;
}

int access(const char *path, int mode) {
    static int (*real)(const char *, int) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "access");
    char buffer[PATH_MAX];
    const char *target = redirected(path, buffer, sizeof buffer);
    int status = real(target, mode);
    if (status != 0) shim_log("spawn-shim: access %s (mode %d) failed: %s\n", path ? path : "(null)", mode,
                              strerror(errno));
    return status;
}

int faccessat(int dirfd, const char *path, int mode, int flags) {
    static int (*real)(int, const char *, int, int) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "faccessat");
    char buffer[PATH_MAX];
    const char *target = redirected(path, buffer, sizeof buffer);
    int status = real(dirfd, target, mode, flags);
    if (status != 0) shim_log("spawn-shim: faccessat %s (mode %d) failed: %s\n", path ? path : "(null)", mode,
                              strerror(errno));
    return status;
}

int fstatat(int dirfd, const char *path, struct stat *info, int flags) {
    static int (*real)(int, const char *, struct stat *, int) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "fstatat");
    char buffer[PATH_MAX];
    const char *target = redirected(path, buffer, sizeof buffer);
    int status = real(dirfd, target, info, flags);
    if (status != 0) shim_log("spawn-shim: fstatat %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return status;
}

int mkdir(const char *path, mode_t mode) {
    static int (*real)(const char *, mode_t) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "mkdir");
    int status = real(path, mode);
    if (status != 0 && errno != EEXIST) shim_log("spawn-shim: mkdir %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return status;
}

int mkdirat(int dirfd, const char *path, mode_t mode) {
    static int (*real)(int, const char *, mode_t) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "mkdirat");
    int status = real(dirfd, path, mode);
    if (status != 0 && errno != EEXIST) shim_log("spawn-shim: mkdirat %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return status;
}

int unlink(const char *path) {
    static int (*real)(const char *) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "unlink");
    int status = real(path);
    if (status != 0 && errno != ENOENT) shim_log("spawn-shim: unlink %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return status;
}

int unlinkat(int dirfd, const char *path, int flags) {
    static int (*real)(int, const char *, int) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "unlinkat");
    int status = real(dirfd, path, flags);
    if (status != 0 && errno != ENOENT) shim_log("spawn-shim: unlinkat %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return status;
}

int rename(const char *from, const char *to) {
    static int (*real)(const char *, const char *) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "rename");
    int status = real(from, to);
    if (status != 0) shim_log("spawn-shim: rename %s -> %s failed: %s\n", from ? from : "(null)", to ? to : "(null)", strerror(errno));
    return status;
}

FILE *fopen(const char *path, const char *mode) {
    static FILE *(*real)(const char *, const char *) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "fopen");
    char buffer[PATH_MAX];
    const char *target = redirected(path, buffer, sizeof buffer);
    FILE *file = real(target, mode);
    if (file == NULL) shim_log("spawn-shim: fopen %s (mode %s) failed: %s\n", path ? path : "(null)",
                               mode ? mode : "?", strerror(errno));
    return file;
}

int stat(const char *path, struct stat *info) {
    static int (*real)(const char *, struct stat *) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "stat");
    char buffer[PATH_MAX];
    return real(redirected(path, buffer, sizeof buffer), info);
}

int lstat(const char *path, struct stat *info) {
    static int (*real)(const char *, struct stat *) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "lstat");
    char buffer[PATH_MAX];
    return real(redirected(path, buffer, sizeof buffer), info);
}

int open(const char *path, int flags, ...) {
    static int (*real)(const char *, int, ...) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "open");
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0 || (flags & O_TMPFILE) == O_TMPFILE) {
        va_list arguments;
        va_start(arguments, flags);
        mode = (mode_t) va_arg(arguments, int);
        va_end(arguments);
    }
    char buffer[PATH_MAX];
    const char *target = redirected(path, buffer, sizeof buffer);
    int fd = real(target, flags, mode);
    if (fd < 0) shim_log("spawn-shim: open %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return fd;
}

int openat(int dirfd, const char *path, int flags, ...) {
    static int (*real)(int, const char *, int, ...) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "openat");
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0 || (flags & O_TMPFILE) == O_TMPFILE) {
        va_list arguments;
        va_start(arguments, flags);
        mode = (mode_t) va_arg(arguments, int);
        va_end(arguments);
    }
    char buffer[PATH_MAX];
    const char *target = redirected(path, buffer, sizeof buffer);
    int fd = real(dirfd, target, flags, mode);
    if (fd < 0) shim_log("spawn-shim: openat %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return fd;
}

int posix_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions,
                const posix_spawnattr_t *attributes, char *const argv[], char *const envp[]) {
    static int (*real)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                       char *const[], char *const[]) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "posix_spawn");
    char buffer[PATH_MAX];
    if (names_env(path) && argv != NULL && argv[0] != NULL && argv[1] != NULL) {
        const char *target = redirected(argv[1], buffer, sizeof buffer);
        shim_log("spawn-shim: posix_spawn %s -> %s (env replaced)\n", path, target);
        return real(pid, target, actions, attributes, argv + 1, envp);
    }
    const char *target = redirected(path, buffer, sizeof buffer);
    char **filtered = NULL;
    if (names_tar_binary(target)) without_preload(envp, &filtered);
    int status = real(pid, target, actions, attributes, argv, filtered != NULL ? filtered : envp);
    free(filtered);
    shim_log("spawn-shim: posix_spawn %s%s%s (status %d) pid %d\n", target, target == path ? "" : " (redirected)",
             filtered != NULL ? " (preload dropped)" : "", status,
             status == 0 && pid != NULL ? (int) *pid : -1);
    if (status != 0) shim_log("spawn-shim: posix_spawn %s failed: %s\n", target, strerror(status));
    return status;
}

int posix_spawnp(pid_t *pid, const char *file, const posix_spawn_file_actions_t *actions,
                 const posix_spawnattr_t *attributes, char *const argv[], char *const envp[]) {
    static int (*real)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                       char *const[], char *const[]) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "posix_spawnp");
    char buffer[PATH_MAX];
    if (names_env(file) && argv != NULL && argv[0] != NULL && argv[1] != NULL) {
        const char *target = redirected(argv[1], buffer, sizeof buffer);
        shim_log("spawn-shim: posix_spawnp %s -> %s (env replaced)\n", file, target);
        return real(pid, target, actions, attributes, argv + 1, envp);
    }
    const char *target = redirected(file, buffer, sizeof buffer);
    char **filtered = NULL;
    if (names_tar_binary(target)) without_preload(envp, &filtered);
    int status = real(pid, target, actions, attributes, argv, filtered != NULL ? filtered : envp);
    free(filtered);
    if (status != 0) shim_log("spawn-shim: posix_spawnp %s failed: %s\n", target, strerror(status));
    return status;
}

int execve(const char *path, char *const argv[], char *const envp[]) {
    static int (*real)(const char *, char *const[], char *const[]) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "execve");
    char buffer[PATH_MAX];
    if (names_env(path) && argv != NULL && argv[0] != NULL && argv[1] != NULL) {
        const char *target = redirected(argv[1], buffer, sizeof buffer);
        shim_log("spawn-shim: execve %s -> %s (env replaced)\n", path, target);
        return real(target, argv + 1, envp);
    }
    const char *target = redirected(path, buffer, sizeof buffer);
    char **filtered = NULL;
    if (names_tar_binary(target)) without_preload(envp, &filtered);
    int status = real(target, argv, filtered != NULL ? filtered : envp);
    free(filtered);   /* reached only when execve failed */
    shim_log("spawn-shim: execve %s failed: %s\n", target, strerror(errno));
    return status;
}
