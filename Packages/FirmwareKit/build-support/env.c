// env(1), the little bit of it firmwarekit's iPad 1 (k48) recipe needs: run the program named by the first
// argument with the rest of the arguments, which is how it launches iBoot32Patcher.
//
// On macOS that name is looked up on PATH by Foundation's Process (Darwin spawns through posix_spawnp), and
// /usr/bin/env exists anyway. Android has neither: bionic's posix_spawn does not search PATH, so a bare "env"
// resolves against the working directory only, and /usr/bin does not exist. The APK therefore carries this
// binary as env next to firmwarekit in nativeLibraryDir, and FirmwareService starts firmwarekit with that
// directory as its working directory, so the name resolves to this file and the patcher then runs from the path
// FIRMWAREKIT_IBOOT_PATCHER names.
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "env: no program to run\n");
        return 127;
    }
    execv(argv[1], &argv[1]);
    fprintf(stderr, "env: %s: %s\n", argv[1], strerror(errno));
    return 127;
}
