//
// Created by kamil on 05.01.2020.
//

#ifndef SERVER_SERVER_H
#define SERVER_SERVER_H
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <netdb.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

#define SERVER_PORT 4321
#define QUEUE_SIZE 5

#endif //SERVER_SERVER_H
