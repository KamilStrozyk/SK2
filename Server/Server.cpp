#include "Server.h"

struct thread_data_t {
    int player_descriptor;
    int enemy_descriptor;
    char message[256];
    int message_length;
    char rcvd[2];
    int our_board[10][10];
    int enemy_board[10][10];
    bool has_our_board = false;
    bool has_enemy_board = false;
    bool game_started = false;
    bool first_turn_ours = false;
};

pthread_mutex_t lock;

// reusable write function with mutex, nothing special at all
void Write(char *message, int descriptor) {
    pthread_mutex_lock(&lock);
    write(descriptor, message, 256);
    printf("%s", message);
    pthread_mutex_unlock(&lock);
}

// reusable turn message
void SetTurnMessage(int turn_descriptor, int not_turn_descriptor) {
    char *turn_message = (char *) malloc(sizeof(char) * 9);
    strcpy(turn_message, "ourturn\n");
    Write(turn_message, turn_descriptor);
    free(turn_message);

    char *not_turn_message = (char *) malloc(sizeof(char) * 9);
    strcpy(not_turn_message, "turn\n");
    Write(not_turn_message, not_turn_descriptor);
    free(not_turn_message);
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
        printf("%s", (*th_data).message);


        // this is where the fun begins
        // message interpretation
        //char *messageHeader = (char *) malloc(
        //       sizeof(char) * 5); // len(mine headers) = 5, mine headers: check:, Player, board:



        if (strstr((*th_data).message, "end") != NULL) // error of end of game- end of thread
        {
            pthread_exit(NULL);
        } else if (strstr((*th_data).message, "Player") !=
                   NULL) // while connecting- receive player name and send to enemy
        {
            char *enemy_message = (char *) malloc(sizeof(char) * ((*th_data).message_length) + 6);

            strcpy(enemy_message, "enemy:");
            for (int i = 6; i < (*th_data).message_length; i++) enemy_message[i] = (*th_data).message[i];

            Write(enemy_message, (*th_data).enemy_descriptor);
            free(enemy_message);
        } else if (strstr((*th_data).message, "board") != NULL &&
                   !(*th_data).has_our_board) // send board initial state from player
        {
            // TODO: handle sending board to enemy in smarter way, this is kinda stupid
            Write((*th_data).message, (*th_data).enemy_descriptor);
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    (*th_data).our_board[i][j] = (*th_data).message[i + j + 6];
                }
            }
            (*th_data).has_our_board = true;
        } else if (strstr((*th_data).message, "boare") != NULL &&
                   !(*th_data).has_enemy_board) // send board initial state from enemy
        {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    (*th_data).enemy_board[i][j] = (*th_data).message[i + j + 6];
                }
            }
            (*th_data).has_enemy_board = true;
        } else if (strstr((*th_data).message, "check") != NULL) // check field chosen by player
        {
            //point coords
            int x = (*th_data).message[5] - '0';
            int y = (*th_data).message[6] - '0';
            // 0- nothing/sank; <1;4> ships ; <5;8> hit ships
            if ((*th_data).enemy_board[x][y] == 0) {

                SetTurnMessage((*th_data).enemy_descriptor, (*th_data).player_descriptor);
            } else if ((*th_data).enemy_board[x][y] >0) {


                SetTurnMessage((*th_data).player_descriptor, (*th_data).enemy_descriptor);
            }


        } else if ((*th_data).has_enemy_board && (*th_data).has_our_board &&
                   !(*th_data).game_started) { // when we have board from enemy and ourselves
            char *start_message = (char *) malloc(sizeof(char) * 6);
            strcpy(start_message, "start");
            Write(start_message, (*th_data).player_descriptor);
            Write(start_message, (*th_data).enemy_descriptor);
            free(start_message);
            (*th_data).game_started = true;

            //set first turn
            if ((*th_data).first_turn_ours) {
                SetTurnMessage((*th_data).player_descriptor, (*th_data).enemy_descriptor);
            }

        } else if ((*th_data).game_started) { // when our ships are destroyed- sum of array is 0
            int *sum = (int *) malloc(sizeof(int));
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    sum += (*th_data).enemy_board[i][j];
                }
            }
            if (sum == 0) { //send lose to player and win to enemy
                char *lose_message = (char *) malloc(sizeof(char) * 6);
                strcpy(lose_message, "lose\n");
                Write(lose_message, (*th_data).enemy_descriptor);
                free(lose_message);

                char *win_message = (char *) malloc(sizeof(char) * 6);
                strcpy(win_message, "won\n");
                Write(win_message, (*th_data).player_descriptor);
                free(win_message);
            }
            free(sum);
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
