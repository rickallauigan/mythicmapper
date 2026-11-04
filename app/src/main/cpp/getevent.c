#include <stdio.h>
#include <signal.h>
#include <unistd.h>
#include <sys/prctl.h>

static void __attribute ((constructor))
getevent(void) {
    /* This preload belongs only to MythicMapper's reader child. Even abrupt parent
       death must not leave a privileged, idle getevent reader behind. */
    prctl(PR_SET_PDEATHSIG, SIGTERM);
    if (getppid() == 1) raise(SIGTERM);
    setlinebuf(stdout);
}
