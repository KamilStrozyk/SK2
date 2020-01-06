#include "Server.h"

struct thread_data_t {
    int player1_descriptor;
    int player2_descriptor;
    char message[256];
    char rcvd[2];
    //char sent_message[256];
};

// reusable write function with mutex, nothing special at all
void Write(char *message, int descriptor) {
    //pthread_detach(pthread_self());
    //struct thread_data_t *th_data = (struct thread_data_t *) t_data;
 //   while (1) {
        //scanf("%s", (*th_data).sent_message);
        //(*th_data).sent_message[strlen((*th_data).sent_message)] = '\n';
        write(descriptor, message, strlen(message));
    //}

}

// thread for a player
void *PlayerThread(void *t_data) {
    pthread_detach(pthread_self());
    struct thread_data_t *th_data = (struct thread_data_t *) t_data;
    while (1) {
        for (int i = 0; (*th_data).rcvd[0] != '\n'; i++)
        {
            read((*th_data).player1_descriptor, (*th_data).rcvd, 1);
            (*th_data).message[i] = (*th_data).rcvd[0];
        }
        (*th_data).rcvd[0] = 0;
        printf("%s", (*th_data).message);
        memset((*th_data).message, 0, (sizeof(char)) * 256);
        Write("enemy: Hitler\n", (*th_data).player1_descriptor);
    }
    pthread_exit(NULL);
}

// prepare game and threads for players
void handleConnection(int player1_descriptor) {
    int create_result = 0;

    pthread_t player_thread;

    thread_data_t *t_data = (thread_data_t *) malloc(sizeof(thread_data_t));

    t_data->player1_descriptor = player1_descriptor;
    create_result = pthread_create(&player_thread, NULL, PlayerThread, (void *) t_data);

    if (create_result) {
        printf("Error during player thread creation, code: %d\n", create_result);
        exit(-1);
    }
}

int main(int argc, char *argv[]) {
    int server_socket_descriptor;
    int connection_socket_descriptor;
    int bind_result;
    int listen_result;
    char reuse_addr_val = 1;
    struct sockaddr_in server_address;

    memset(&server_address, 0, sizeof(struct sockaddr));
    server_address.sin_family = AF_INET;
    server_address.sin_addr.s_addr = htonl(INADDR_ANY);
    server_address.sin_port = htons(SERVER_PORT);

    server_socket_descriptor = socket(AF_INET, SOCK_STREAM, 0);
    if (server_socket_descriptor < 0) {
        fprintf(stderr, "%s: Error during creation server socket. \n", argv[0]);
        exit(1);
    }
    setsockopt(server_socket_descriptor, SOL_SOCKET, SO_REUSEADDR, (char *) &reuse_addr_val, sizeof(reuse_addr_val));

    bind_result = bind(server_socket_descriptor, (struct sockaddr *) &server_address, sizeof(struct sockaddr));
    if (bind_result < 0) {
        fprintf(stderr, "%s: Binding error.\n", argv[0]);
        exit(1);
    }

    listen_result = listen(server_socket_descriptor, QUEUE_SIZE);
    if (listen_result < 0) {
        fprintf(stderr, "%s: Error during setting queue size.\n", argv[0]);
        exit(1);
    }

    while (1) {
        connection_socket_descriptor = accept(server_socket_descriptor, NULL, NULL);
        if (connection_socket_descriptor < 0) {
            fprintf(stderr, "%s: Error during creation connection socket\n", argv[0]);
            exit(1);
        }

        handleConnection(connection_socket_descriptor);
    }

    close(server_socket_descriptor);
    return (0);
}
