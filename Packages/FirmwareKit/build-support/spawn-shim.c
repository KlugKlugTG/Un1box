// A preloaded shim for the firmwarekit process, which is where the iPad 1 (k48) recipe starts iBoot32Patcher.
//
// The recipe builds the spawn as "env <patcher> <in> <out> --rsa ...", a bare name. Foundation finds that name on
// PATH on macOS (Darwin spawns through posix_spawnp) and /usr/bin/env exists there anyway; on Android bionic's
// posix_spawn searches nothing, so the name is resolved against the working directory only, and /usr/bin does not
// exist at all -- the recipe died with "iBoot32Patcher (<path>) not runnable: ... The file doesn't exist." no
// matter which patcher path the app named.
//
// Rather than guess at the spawn from the outside, this library is put in front of it with LD_PRELOAD: it logs
// every access/spawn the process makes to SPAWN_SHIM_LOG, and when the program being spawned is env(1) it runs the
// program env was given, with env's arguments, directly -- which is the whole of env that the recipe needs.
//
// Everything here is libc and dlopen: it is loaded into a statically linked Swift binary, so it must not depend on
// anything the app does not already carry.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <spawn.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define SHIM_LOG_LIMIT 400

static int shim_fd = -2;
static int shim_lines = 0;

static void shim_log(const char *format, ...) {
    if (shim_lines >= SHIM_LOG_LIMIT) return;
    if (shim_fd == -2) {
        const char *path = getenv("SPAWN_SHIM_LOG");
        shim_fd = path && *path ? open(path, O_WRONLY | O_CREAT | O_APPEND, 0644) : -1;
    }
    if (shim_fd < 0) return;
    char line[320];
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
    shim_log("spawn-shim: loaded into pid %d\n", (int) getpid());
}

/** True for the name the recipe launches the patcher through, in every spelling we have seen it try. */
static int names_env(const char *path) {
    if (path == NULL) return 0;
    return strcmp(path, "env") == 0 || strcmp(path, "/usr/bin/env") == 0 ||
           strcmp(path, "/bin/env") == 0 || strcmp(path, "/system/bin/env") == 0;
}

int posix_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions,
                const posix_spawnattr_t *attributes, char *const argv[], char *const envp[]) {
    static int (*real)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                       char *const[], char *const[]) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "posix_spawn");
    if (names_env(path) && argv != NULL && argv[0] != NULL && argv[1] != NULL) {
        shim_log("spawn-shim: posix_spawn %s %s%s -> %s (run directly)\n", path, argv[1], argv[2] ? " ..." : "",
                 argv[1]);
        return real(pid, argv[1], actions, attributes, argv + 1, envp);
    }
    int status = real(pid, path, actions, attributes, argv, envp);
    if (status != 0) shim_log("spawn-shim: posix_spawn %s failed: %s\n", path ? path : "(null)", strerror(status));
    return status;
}

int posix_spawnp(pid_t *pid, const char *file, const posix_spawn_file_actions_t *actions,
                 const posix_spawnattr_t *attributes, char *const argv[], char *const envp[]) {
    static int (*real)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                       char *const[], char *const[]) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "posix_spawnp");
    if (names_env(file) && argv != NULL && argv[0] != NULL && argv[1] != NULL) {
        shim_log("spawn-shim: posix_spawnp %s -> %s (run directly)\n", file, argv[1]);
        return real(pid, argv[1], actions, attributes, argv + 1, envp);
    }
    return real(pid, file, actions, attributes, argv, envp);
}

int execve(const char *path, char *const argv[], char *const envp[]) {
    static int (*real)(const char *, char *const[], char *const[]) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "execve");
    if (names_env(path) && argv != NULL && argv[0] != NULL && argv[1] != NULL) {
        shim_log("spawn-shim: execve %s -> %s (run directly)\n", path, argv[1]);
        return real(argv[1], argv + 1, envp);
    }
    int status = real(path, argv, envp);
    shim_log("spawn-shim: execve %s failed: %s\n", path ? path : "(null)", strerror(errno));
    return status;
}

int access(const char *path, int mode) {
    static int (*real)(const char *, int) = NULL;
    if (real == NULL) real = dlsym(RTLD_NEXT, "access");
    int status = real(path, mode);
    if (status != 0) {
        shim_log("spawn-shim: access %s (mode %d) failed: %s\n", path ? path : "(null)", mode, strerror(errno));
    }
    return status;
}
