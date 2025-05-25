package org.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String CREATE_USERS_TABLE = """
            CREATE TABLE IF NOT EXISTS users(
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(50) NOT NULL,
                surname VARCHAR(100) NOT NULL,
                subscribed BOOLEAN NOT NULL DEFAULT FALSE,
                phone VARCHAR(15) NOT NULL
            )""";

    private static final String CREATE_BOOKS_TABLE = """
            CREATE TABLE IF NOT EXISTS books(
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100),
                isbn VARCHAR(100),
                publishing_year INT,
                author VARCHAR(100),
                publisher VARCHAR(100)
            )""";

    private static final String CREATE_MUSIC_TABLE = """
            CREATE TABLE IF NOT EXISTS music(
                id INT PRIMARY KEY,
                name TEXT NOT NULL
            )""";

    private static final String INSERT_MUSIC = """
            INSERT INTO music (id, name)
            SELECT * FROM (VALUES (1, 'Bohemian Rhapsody'),
                   (2, 'Stairway to Heaven'),
                   (3, 'Imagine'),
                   (4, 'Sweet Child O Mine'),
                   (5, 'Hey Jude'),
                   (6, 'Hotel California'),
                   (7, 'Billie Jean'),
                   (8, 'Wonderwall'),
                   (9, 'Smells Like Teen Spirit'),
                   (10, 'Let It Be'),
                   (11, 'I Want It All'),
                   (12, 'November Rain'),
                   (13, 'Losing My Religion'),
                   (14, 'One'),
                   (15, 'With or Without You'),
                   (16, 'Sweet Caroline'),
                   (17, 'Yesterday'),
                   (18, 'Dont Stop Believin'),
                   (19, 'Crazy Train'),
                   (20, 'Always')) AS new_data
            WHERE NOT EXISTS (SELECT 1 FROM music)""";

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            // Инициализация базы данных
            initializeDatabase(connection);

            // 1. Получить список музыкальных композиций
            System.out.println("1. Все музыкальные композиции:");
            List<String> allMusic = getAllMusic(connection);
            allMusic.forEach(System.out::println);

            // 2. Получить композиции без букв 'm' и 't'
            System.out.println("\n2. Композиции без букв 'm' и 't':");
            List<String> filteredMusic = getMusicWithoutLetters(connection, "mt");
            filteredMusic.forEach(System.out::println);

            // 3. Добавить любимую композицию
            System.out.println("\n3. Добавляем новую композицию...");
            addMusic(connection, 21, "Thunderstruck");
            System.out.println("Добавлена новая композиция: Thunderstruck");

            // 4. Работа с книгами и пользователями из JSON
            System.out.println("\n4. Обрабатываем books.json...");
            processBooksJson(connection);

            // 5. Отсортированный список книг по году издания
            System.out.println("\n5. Книги, отсортированные по году издания:");
            List<Book> booksSortedByYear = getBooksSortedByYear(connection);
            booksSortedByYear.forEach(System.out::println);

            // 6. Книги младше 2000 года
            System.out.println("\n6. Книги, изданные до 2000 года:");
            List<Book> booksBefore2000 = getBooksBeforeYear(connection, 2000);
            booksBefore2000.forEach(System.out::println);

            // 7. Добавить информацию о себе и свои любимые книги
            System.out.println("\n7. Добавляем информацию о себе...");
            addPersonalInfo(connection);
            System.out.println("Информация добавлена:");
            printUserBooks(connection, "Александр", "Рубцов");

            // 8. Удаление таблиц
            System.out.println("\n8. Удаляем таблицы...");
            dropTables(connection);
            System.out.println("Таблицы удалены.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void initializeDatabase(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Создаем таблицу music и заполняем данными
            stmt.execute(CREATE_MUSIC_TABLE);
            stmt.execute(INSERT_MUSIC);

            // Создаем таблицы users и books (если они еще не существуют)
            stmt.execute(CREATE_USERS_TABLE);
            stmt.execute(CREATE_BOOKS_TABLE);
        }
    }

    // 1. Получить список музыкальных композиций
    private static List<String> getAllMusic(Connection connection) throws SQLException {
        List<String> music = new ArrayList<>();
        String sql = "SELECT name FROM music";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                music.add(rs.getString("name"));
            }
        }
        return music;
    }

    // 2. Получить композиции без указанных букв (регистронезависимо)
    private static List<String> getMusicWithoutLetters(Connection connection, String letters) throws SQLException {
        List<String> music = new ArrayList<>();
        String sql = "SELECT name FROM music WHERE LOWER(name) NOT LIKE ? AND LOWER(name) NOT LIKE ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "%" + letters.charAt(0) + "%");
            pstmt.setString(2, "%" + letters.charAt(1) + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    music.add(rs.getString("name"));
                }
            }
        }
        return music;
    }

    // 3. Добавить любимую композицию
    private static void addMusic(Connection connection, int id, String name) throws SQLException {
        String sql = "INSERT INTO music (id, name) VALUES (?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
        }
    }

    // 4. Обработка books.json
    private static void processBooksJson(Connection connection) throws SQLException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json;

        try {
            // Читаем содержимое файла books.json
            json = new String(Files.readAllBytes(Paths.get("books.json")));
        } catch (IOException e) {
            System.err.println("Ошибка при чтении файла books.json: " + e.getMessage());
            return;
        }

        // Чтение JSON
        Type userListType = new TypeToken<List<User>>(){}.getType();
        List<User> users = gson.fromJson(json, userListType);

        // Проверка на null после десериализации
        if (users == null) {
            System.err.println("Ошибка: не удалось загрузить данные из books.json");
            return;
        }

        // Добавление пользователей и книг в БД
        for (User user : users) {
            // Проверяем, существует ли пользователь
            if (!userExists(connection, user)) {
                addUser(connection, user);
            }

            // Добавляем книги пользователя (проверяем, что favoriteBooks не null)
            if (user.getFavoriteBooks() != null) {
                for (Book book : user.getFavoriteBooks()) {
                    if (!bookExists(connection, book)) {
                        addBook(connection, book);
                    }
                }
            }
        }
    }

    private static boolean userExists(Connection connection, User user) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE name = ? AND surname = ? AND phone = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, user.getName());
            pstmt.setString(2, user.getSurname());
            pstmt.setString(3, user.getPhone());

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void addUser(Connection connection, User user) throws SQLException {
        String sql = "INSERT INTO users (name, surname, subscribed, phone) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, user.getName());
            pstmt.setString(2, user.getSurname());
            pstmt.setBoolean(3, user.isSubscribed());
            pstmt.setString(4, user.getPhone());
            pstmt.executeUpdate();
        }
    }

    private static boolean bookExists(Connection connection, Book book) throws SQLException {
        String sql = "SELECT 1 FROM books WHERE isbn = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, book.getIsbn());

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void addBook(Connection connection, Book book) throws SQLException {
        String sql = "INSERT INTO books (name, isbn, publishing_year, author, publisher) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, book.getName());
            pstmt.setString(2, book.getIsbn());
            pstmt.setInt(3, book.getPublishingYear());
            pstmt.setString(4, book.getAuthor());
            pstmt.setString(5, book.getPublisher());
            pstmt.executeUpdate();
        }
    }

    // 5. Отсортированный список книг по году издания
    private static List<Book> getBooksSortedByYear(Connection connection) throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books ORDER BY publishing_year";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                books.add(new Book(
                        rs.getString("name"),
                        rs.getString("isbn"),
                        rs.getInt("publishing_year"),
                        rs.getString("author"),
                        rs.getString("publisher")
                ));
            }
        }
        return books;
    }

    // 6. Книги до указанного года
    private static List<Book> getBooksBeforeYear(Connection connection, int year) throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE publishing_year < ? ORDER BY publishing_year";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, year);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    books.add(new Book(
                            rs.getString("name"),
                            rs.getString("isbn"),
                            rs.getInt("publishing_year"),
                            rs.getString("author"),
                            rs.getString("publisher")
                    ));
                }
            }
        }
        return books;
    }

    // 7. Добавить информацию о себе
    private static void addPersonalInfo(Connection connection) throws SQLException {
        // Добавляем себя как пользователя
        User me = new User("Александр", "Рубцов", true, "8***2699236");
        if (!userExists(connection, me)) {
            addUser(connection, me);
        }

        // Добавляем любимые книги
        Book[] myBooks = {
                new Book("Martin Iden", "9780132350884", 2008, "Jack London", "Jack London"),
                new Book("Effective Java", "9780134685991", 2018, "Joshua Bloch", "Addison-Wesley")
        };

        for (Book book : myBooks) {
            if (!bookExists(connection, book)) {
                addBook(connection, book);
            }
        }
    }

    // Вывод информации о пользователе и его книгах
    private static void printUserBooks(Connection connection, String name, String surname) throws SQLException {
        String userSql = "SELECT * FROM users WHERE name = ? AND surname = ?";
        String booksSql = """
            SELECT b.* FROM books b
            JOIN users u ON 1=1
            WHERE u.name = ? AND u.surname = ?
            ORDER BY b.publishing_year""";

        try (PreparedStatement userStmt = connection.prepareStatement(userSql)) {
            userStmt.setString(1, name);
            userStmt.setString(2, surname);

            try (ResultSet userRs = userStmt.executeQuery()) {
                if (userRs.next()) {
                    User user = new User(
                            userRs.getString("name"),
                            userRs.getString("surname"),
                            userRs.getBoolean("subscribed"),
                            userRs.getString("phone")
                    );
                    System.out.println("Пользователь: " + user);

                    try (PreparedStatement booksStmt = connection.prepareStatement(booksSql)) {
                        booksStmt.setString(1, name);
                        booksStmt.setString(2, surname);

                        try (ResultSet booksRs = booksStmt.executeQuery()) {
                            System.out.println("Любимые книги:");
                            while (booksRs.next()) {
                                Book book = new Book(
                                        booksRs.getString("name"),
                                        booksRs.getString("isbn"),
                                        booksRs.getInt("publishing_year"),
                                        booksRs.getString("author"),
                                        booksRs.getString("publisher")
                                );
                                System.out.println("  " + book);
                            }
                        }
                    }
                }
            }
        }
    }

    // 8. Удаление таблиц
    private static void dropTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("DROP TABLE IF EXISTS books");
            stmt.execute("DROP TABLE IF EXISTS music");
        }
    }

    // Классы для хранения данных
    static class User {
        private String name;
        private String surname;
        private boolean subscribed;
        private String phone;
        private List<Book> favoriteBooks;

        public User(String name, String surname, boolean subscribed, String phone) {
            this.name = name;
            this.surname = surname;
            this.subscribed = subscribed;
            this.phone = phone;
        }

        // Геттеры и сеттеры
        public String getName() { return name; }
        public String getSurname() { return surname; }
        public boolean isSubscribed() { return subscribed; }
        public String getPhone() { return phone; }
        public List<Book> getFavoriteBooks() { return favoriteBooks; }

        @Override
        public String toString() {
            return String.format("%s %s, телефон: %s, подписка: %s",
                    name, surname, phone, subscribed ? "активна" : "неактивна");
        }
    }

    static class Book {
        private String name;
        private String isbn;
        private int publishingYear;
        private String author;
        private String publisher;

        public Book(String name, String isbn, int publishingYear, String author, String publisher) {
            this.name = name;
            this.isbn = isbn;
            this.publishingYear = publishingYear;
            this.author = author;
            this.publisher = publisher;
        }

        // Геттеры
        public String getName() { return name; }
        public String getIsbn() { return isbn; }
        public int getPublishingYear() { return publishingYear; }
        public String getAuthor() { return author; }
        public String getPublisher() { return publisher; }

        @Override
        public String toString() {
            return String.format("%s (%s), автор: %s, год: %d, издатель: %s",
                    name, isbn, author, publishingYear, publisher);
        }
    }
}