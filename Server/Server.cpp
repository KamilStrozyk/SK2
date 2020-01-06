#include "Server.h"

struct thread_data_t {
    int descriptor_no;
    char rcvd_message[1024];
    char sent_message[1024];
};

void *ReadThread(void *t_data) {
    pthread_detach(pthread_self());
    struct thread_data_t *th_data = (struct thread_data_t *) t_data;
    while (1) {
        int data = read((*th_data).descriptor_no, (*th_data).rcvd_message, 512);
        printf("%s", (*th_data).rcvd_message);
        memset((*th_data).rcvd_message, 0, (sizeof (char))*1024);
    }
    pthread_exit(NULL);
}

void *WriteThread(void *t_data) {
    pthread_detach(pthread_self());
    struct thread_data_t *th_data = (struct thread_data_t *) t_data;
    while (1) {
        scanf("%s", (*th_data).sent_message);
        (*th_data).sent_message[strlen((*th_data).sent_message)] = '\n';
        write((*th_data).descriptor_no, (*th_data).sent_message, strlen((*th_data).sent_message));
    }
    pthread_exit(NULL);
}


void handleConnection(int connection_socket_descriptor) {
    int create_result = 0;

    pthread_t reading_thread;
    pthread_t writing_thread;

    thread_data_t *t_data = (thread_data_t *) malloc(sizeof(thread_data_t));

    t_data->descriptor_no = connection_socket_descriptor;
    create_result = pthread_create(&reading_thread, NULL, ReadThread, (void *) t_data);
    create_result = pthread_create(&writing_thread, NULL, WriteThread, (void *) t_data);
    if (create_result) {
        printf("Error during thread creation, code: %d\n", create_result);
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
