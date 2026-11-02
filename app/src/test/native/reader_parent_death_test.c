// SPDX-License-Identifier: GPL-3.0-or-later
#define _GNU_SOURCE
#include "../../main/cpp/getevent.c"
#include <assert.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <time.h>

int main(void) {
    assert(prctl(PR_SET_CHILD_SUBREAPER,1)==0);
    int pid_pipe[2];assert(pipe(pid_pipe)==0);
    pid_t parent=fork();assert(parent>=0);
    if(parent==0) {
        close(pid_pipe[0]);int ready[2];assert(pipe(ready)==0);
        pid_t child=fork();assert(child>=0);
        if(child==0) {
            close(ready[0]);getevent();
            assert(write(ready[1],"r",1)==1);for(;;)pause();
        }
        close(ready[1]);char marker;assert(read(ready[0],&marker,1)==1);
        assert(write(pid_pipe[1],&child,sizeof(child))==sizeof(child));_exit(0);
    }
    close(pid_pipe[1]);pid_t child;assert(read(pid_pipe[0],&child,sizeof(child))==sizeof(child));close(pid_pipe[0]);
    int status;assert(waitpid(parent,&status,0)==parent);
    for(int i=0;i<200;i++) {
        if(waitpid(child,&status,WNOHANG)==child) {
            assert(WIFSIGNALED(status)&&WTERMSIG(status)==SIGTERM);
            puts("reader parent-death cleanup passed");return 0;
        }
        struct timespec delay={.tv_nsec=10000000};nanosleep(&delay,NULL);
    }
    kill(child,SIGKILL);waitpid(child,&status,0);assert(!"reader survived parent death");
}
