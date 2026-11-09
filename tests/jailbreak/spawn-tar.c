/* What firmwarekit's Cydia step does with tar, without firmwarekit: spawn "/usr/bin/tar" with the
 * recipe's own arguments. Run under LD_PRELOAD of the spawn shim (build-support/spawn-shim.c), that
 * path is what the shim rewrites to the tar the APK carries -- on the host in this test, on the phone
 * at preparation time -- so this is the check that the redirect works through a real spawn, arguments
 * and all, rather than only in the shim's text.
 *
 * Its arguments are the tar invocation's (e.g. "-tf", "-tvf", "-xkf"), its stdout and stderr the
 * child's, and its exit status the child's, so the caller can compare both with the host's tar.
 */
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

extern char **environ;

int main(int argc, char **argv) {
    char **arguments = calloc((size_t) argc + 2, sizeof *arguments);
    if (arguments == NULL) {
        perror("calloc");
        return 1;
    }
    arguments[0] = (char *) "tar";
    for (int i = 1; i < argc; i++) arguments[i] = argv[i];
    arguments[argc] = NULL;

    pid_t pid = 0;
    int status = posix_spawn(&pid, "/usr/bin/tar", NULL, NULL, arguments, environ);
    if (status != 0) {
        fprintf(stderr, "spawn-tar: posix_spawn /usr/bin/tar: %s\n", strerror(status));
        return 1;
    }
    int waited = 0;
    if (waitpid(pid, &waited, 0) < 0) {
        perror("waitpid");
        return 1;
    }
    fprintf(stderr, "spawn-tar: child status %d\n", WIFEXITED(waited) ? WEXITSTATUS(waited) : -1);
    return WIFEXITED(waited) ? WEXITSTATUS(waited) : 1;
}
