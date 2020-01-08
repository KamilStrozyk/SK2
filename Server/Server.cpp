#include "Server.h"

struct thread_data_t {
    int player_descriptor;
    int enemy_descriptor;
    int sum;
    char message_to_send[256];
    char message[256];
    int message_length;
    char rcvd[2];
    int enemy_board[10][10];
    bool has_our_board = false;
    bool has_enemy_board = false;
    bool game_started = false;
    bool first_turn_ours = false;
};

pthread_mutex_t lock;

// reusable write function with mutex, nothing special at all
void Write(char *message, int descriptor) {
   //pthread_mutex_lock(&lock);
    message[strlen(message)] = '\n';
    if (write(descriptor, message, strlen(message)) != -1) printf("%s", message);
   //pthread_mutex_unlock(&lock);
}

// reusable turn message
void SetTurnMessage(int turn_descriptor, int not_turn_descriptor, thread_data_t *th_data) {


    strcpy((*th_data).message_to_send, "ourturn");
    Write((*th_data).message_to_send, turn_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);


    strcpy((*th_data).message_to_send, "turn");
    Write((*th_data).message_to_send, not_turn_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

}

// Omiss to enemy, miss to our
void MarkMiss(thread_data_t *th_data, int x, int y) {
    strcpy((*th_data).message_to_send, "miss");
    for (int i = 5; i <= 6; i++)(*th_data).message_to_send[i - 1] = (*th_data).message[i];
    Write((*th_data).message_to_send, (*th_data).player_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);


    strcpy((*th_data).message_to_send, "Omiss");
    for (int i = 5; i <= 6; i++) (*th_data).message_to_send[i] = (*th_data).message[i];
    Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

    SetTurnMessage((*th_data).enemy_descriptor, (*th_data).player_descriptor, th_data);
}

void MarkSink(thread_data_t *th_data, int x, int y) {
    // Osink to enemy, sink to our
    strcpy((*th_data).message_to_send, "sink");
    for (int i = 5; i <= 6; i++)(*th_data).message_to_send[i - 1] = (*th_data).message[i];
    Write((*th_data).message_to_send, (*th_data).player_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);


    strcpy((*th_data).message_to_send, "Osink");
    for (int i = 5; i <= 6; i++) (*th_data).message_to_send[i] = (*th_data).message[i];
    Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);


    (*th_data).enemy_board[x][y] = 0;
    SetTurnMessage((*th_data).player_descriptor, (*th_data).enemy_descriptor, th_data);
}

void CheckSink(thread_data_t *th_data, int x, int y, int value) {
    // for 2
    //printf("Value:%d", value);
    if (value == 6) {
        if (x > 0 && (*th_data).enemy_board[x - 1][y] == value) {
            MarkSink(th_data, x - 1, y);
            MarkSink(th_data, x, y);
        } else if (x < 9 && (*th_data).enemy_board[x + 1][y] == value) {
            MarkSink(th_data, x + 1, y);
            MarkSink(th_data, x, y);
        } else if (y > 0 && (*th_data).enemy_board[x][y - 1] == value) {
            MarkSink(th_data, x, y - 1);
            MarkSink(th_data, x, y);
        } else if (y < 9 && (*th_data).enemy_board[x][y + 1] == value) {
            MarkSink(th_data, x, y + 1);
            MarkSink(th_data, x, y);
        }
    }


}

// Ohit to enemy, hit to our
void MarkHit(thread_data_t *th_data, int x, int y) {
    strcpy((*th_data).message_to_send, "hit");
    for (int i = 5; i <= 6; i++) (*th_data).message_to_send[i - 2] = (*th_data).message[i];
    Write((*th_data).message_to_send, (*th_data).player_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

    strcpy((*th_data).message_to_send, "Ohit");
    for (int i = 5; i <= 6; i++)(*th_data).message_to_send[i - 1] = (*th_data).message[i];
    Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
    memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

    (*th_data).enemy_board[x][y] += 4;
//  checking sink

    CheckSink(th_data, x, y, (*th_data).enemy_board[x][y]);

    SetTurnMessage((*th_data).player_descriptor, (*th_data).enemy_descriptor, th_data);
}

// thread for a player
void *PlayerThread(void *t_data) {
    pthread_detach(pthread_self());
    struct thread_data_t *th_data = (struct thread_data_t *) t_data;
    // reading byte after byte
    while (1) {
        for (int i = 0; (*th_data).rcvd[0] != '\n'; i++) {
            read((*th_data).player_descriptor, (*th_data).rcvd, 1);
            (*th_data).message[i] = (*th_data).rcvd[0];
            (*th_data).message_length = i + 1;
        }
        //printf("Message:%s", (*th_data).message);
       // printf("desc:%d;%d", (*th_data).player_descriptor, (*th_data).enemy_descriptor);

        // this is where the fun begins
        // message interpretation
        if (strstr((*th_data).message, "end") != NULL) //  end of game- end of thread
        {
            pthread_exit(NULL);
        }
        if (strstr((*th_data).message, "error") != NULL) // error - end of thread
        {
            Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
            pthread_exit(NULL);
        }
        if (strstr((*th_data).message, "Player") !=
            NULL) // while connecting- receive player name and send to enemy
        {

            strcpy((*th_data).message_to_send, "enemy:");
            for (int i = 6; i < (*th_data).message_length; i++) (*th_data).message_to_send[i] = (*th_data).message[i];

            Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
            memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

        }
        if (strstr((*th_data).message, "board") != NULL &&
            !(*th_data).has_our_board) // send board initial state from player
        {
            // TODO: handle sending board to enemy in smarter way, this is kinda stupid
            Write((*th_data).message, (*th_data).enemy_descriptor);
            (*th_data).has_our_board = true;
        }
        if (strstr((*th_data).message, "boare") != NULL &&
            !(*th_data).has_enemy_board) // send board initial state from enemy
        {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    (*th_data).enemy_board[j][i] = (*th_data).message[i * 10 + j + 6] - '0';
                }
            }
            (*th_data).has_enemy_board = true;
        }
        if (strstr((*th_data).message, "check") != NULL) // check field chosen by player
        {
            //point coords
            int y = (*th_data).message[5] - '0';
            int x = (*th_data).message[6] - '0';
            // 0- nothing/sank; <1;4> ships ; <5;8> hit ships
            if ((*th_data).enemy_board[x][y] == 0) {
                MarkMiss(th_data, x, y);
            } else if ((*th_data).enemy_board[x][y] == 1) {
                MarkSink(th_data, x, y);
            } else if ((*th_data).enemy_board[x][y] >= 2 &&
                       (*th_data).enemy_board[x][y] <= 4) {
                MarkHit(th_data, x, y);
            }


        }
        if ((*th_data).has_enemy_board && (*th_data).has_our_board && (*th_data).has_our_board &&

            !(*th_data).game_started) { // when we have board from enemy and ourselves and we are first player

            strcpy((*th_data).message_to_send, "start");
            Write((*th_data).message_to_send, (*th_data).player_descriptor);
            //Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
            memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

            (*th_data).game_started = true;
            if ((*th_data).first_turn_ours)
                SetTurnMessage((*th_data).player_descriptor, (*th_data).enemy_descriptor, th_data);
        }
        if ((*th_data).game_started && (*th_data).has_enemy_board &&
            (*th_data).has_our_board) { // when our ships are destroyed- sum of array is 0
            (*th_data).sum = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    (*th_data).sum += (*th_data).enemy_board[i][j];
                }
            }
            //printf("%d", (*th_data).sum);
            //printf("\n");
            if ((*th_data).sum == 0) { //send lose to player and win to enemy


                strcpy((*th_data).message_to_send, "lose");
                Write((*th_data).message_to_send, (*th_data).enemy_descriptor);
                memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);


                strcpy((*th_data).message_to_send, "won");
                Write((*th_data).message_to_send, (*th_data).player_descriptor);
                memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

            }
            memset((*th_data).message_to_send, 0, (sizeof(char)) * 256);

        }

        // cleaning buffers
        (*th_data).rcvd[0] = 0;
        memset((*th_data).message, 0, (sizeof(char)) * 256);
    }
    pthread_exit(NULL);
}

// prepare game and threads for players
void handleConnection(int player_descriptor, int enemy_descriptor, bool first_turn_ours) {
    int create_result = 0;

    pthread_t player_thread;

    thread_data_t *t_data = (thread_data_t *) malloc(sizeof(thread_data_t));

    t_data->player_descriptor = player_descriptor;
    t_data->enemy_descriptor = enemy_descriptor;
    t_data->first_turn_ours = first_turn_ours;

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
    int game_pair_descriptors[2];
    int game_pair_counter = 0;
    char reuse_addr_val = 1;
    struct sockaddr_in server_address;

    memset(&server_address, 0, sizeof(struct sockaddr));
    server_address.sin_family = AF_INET;
    server_address.sin_addr.s_addr = htonl(INADDR_ANY);
    server_address.sin_port = htons(SERVER_PORT);

    if (pthread_mutex_init(&lock, NULL) != 0) {
        printf("%s: Mutex init failed\n", argv[0]);
        exit(1);
    }

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
        // grouping players to game
        game_pair_descriptors[game_pair_counter++] = connection_socket_descriptor;
        if (game_pair_counter == 2) {
            // threads for both players, we're sending player and enemy descriptor
            handleConnection(game_pair_descriptors[0], game_pair_descriptors[1], true);
            handleConnection(game_pair_descriptors[1], game_pair_descriptors[0], false);
            game_pair_counter = 0;
        }
    }

    close(server_socket_descriptor);
    return (0);
}
