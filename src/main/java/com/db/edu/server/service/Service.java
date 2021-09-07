package com.db.edu.server.service;

import com.db.edu.exception.DuplicateNicknameException;
import com.db.edu.exception.MessageTooLongException;
import com.db.edu.exception.UserNotIdentifiedException;
import com.db.edu.server.storage.BufferStorage;
import com.db.edu.server.storage.RoomStorage;
import com.db.edu.server.UsersController;
import com.db.edu.server.model.User;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.db.edu.server.UsersController.isNicknameTaken;
import static com.db.edu.server.UsersController.sendMessageToUser;
import static com.db.edu.server.storage.RoomStorage.*;

public class Service {

    /**
     * Saves a message from user to a room file and give it to other users.
     * The message can not be longer than 150 characters and from an unidentified user.
     * @param message
     * @param user
     * @throws UserNotIdentifiedException
     * @throws MessageTooLongException
     */
    public void saveAndSendMessage(String message, User user) throws UserNotIdentifiedException,
            MessageTooLongException, IOException {
        checkMessageLength(message);
        checkUserIdentified(user);
        String formattedMessage = formatMessage(user.getNickname(), message);
        BufferedWriter writer = getWriter(getFileName(user.getRoomName()));
        saveMessage(writer, formattedMessage);
        UsersController.sendMessageToAllUsers(formattedMessage, RoomStorage.getUsersByRoomId(user.getRoomId()));
    }


    /**
     * Get all messages from a room file to the user. User must be identified.
     * @param user
     * @throws UserNotIdentifiedException
     * @throws IOException
     */
    public void getMessagesFromRoom(User user) throws UserNotIdentifiedException, IOException {
        checkUserIdentified(user);
        List<String> lines = Files.readAllLines(Paths.get(getFileName(user.getRoomName())));
        UsersController.sendAllMessagesToUser(lines, user.getId());
    }

    /**
     * Saves or changes the user's nickname. A nickname cannot repeat an existing one.
     * @param nickname
     * @param user
     * @throws DuplicateNicknameException
     */
    public void setUserNickname(String nickname, User user) throws DuplicateNicknameException {
        if (isNicknameTaken(nickname)) {
            throw new DuplicateNicknameException();
        }
        user.setNickname(nickname);
        sendMessageToUser("Nickname successfully set!", user.getId());
    }

    /**
     * Removes the user's room that he was in and adds another room to him.
     * @param roomName
     * @param user
     */
    public void setUserRoom(String roomName, User user) {
        if (user.getRoomId() != 0) {
            removeUserFromRoom(user.getId(), user.getRoomId());
        }
        Integer roomId = addUserToRoom(user.getId(), roomName);
        user.setRoomId(roomId);
        user.setRoomName(roomName);
        sendMessageToUser("Joined #" + roomName + "!", user.getId());
    }

    void saveMessage(BufferedWriter writer, String formattedMessage) throws IOException {
        writer.write(formattedMessage);
        writer.newLine();
        writer.flush();
    }

    String formatMessage(String nickname, String message) {
        return nickname + ": " + message + " ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")";
    }

    BufferedWriter getWriter(String fileName) {
        BufferedWriter writer = BufferStorage.getBufferedWriterByFileName(fileName);
        Path filePath = Paths.get(fileName);
        if (writer == null) {
            try {
                if (!Files.exists(filePath)) {
                    Files.createDirectories(filePath.getParent());
                    Files.createFile(filePath);
                }
                writer = new BufferedWriter(
                        new OutputStreamWriter(
                                new BufferedOutputStream(
                                        new FileOutputStream(fileName, true)), StandardCharsets.UTF_8));
                BufferStorage.save(fileName, writer);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
        return writer;
    }

    void checkUserIdentified(User user) throws UserNotIdentifiedException {
        if (user.getNickname() == null) {
            throw new UserNotIdentifiedException();
        }
    }

    void checkMessageLength(String message) throws MessageTooLongException {
        if (message.length() > 150) {
            throw new MessageTooLongException();
        }
    }
}