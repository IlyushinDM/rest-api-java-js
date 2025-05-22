-- Таблица студентов
CREATE TABLE IF NOT EXISTS students (
    student_id SERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    group_name VARCHAR(50),
    password_hash VARCHAR(255) NOT NULL
);

-- Таблица курсов
CREATE TABLE IF NOT EXISTS courses (
    course_id SERIAL PRIMARY KEY,
    course_name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT
);

-- Таблица для связи студентов и курсов (расписание/запись на курс)
CREATE TABLE IF NOT EXISTS student_courses (
    student_course_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    course_id INT NOT NULL REFERENCES courses(course_id) ON DELETE CASCADE,
    enrollment_date DATE DEFAULT CURRENT_DATE,
    UNIQUE (student_id, course_id)
);

-- Таблица успеваемости
CREATE TABLE IF NOT EXISTS grades (
    grade_id SERIAL PRIMARY KEY,
    student_course_id INT NOT NULL REFERENCES student_courses(student_course_id) ON DELETE CASCADE,
    grade_value VARCHAR(10),
    grade_date DATE DEFAULT CURRENT_DATE,
    comments TEXT
);

-- Таблица документов (метаданные)
CREATE TABLE IF NOT EXISTS documents (
    document_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(50),
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_path VARCHAR(512) NOT NULL
);

-- Таблица сотрудников/администраторов
CREATE TABLE IF NOT EXISTS employees (
    employee_id SERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL
    role VARCHAR(50) NOT NULL DEFAULT 'ADMIN'
);

-- Пример администратора для тестов
-- Пароль 'adminpass'
--INSERT INTO employees (first_name, last_name, email, password_hash, role)
--VALUES ('Admin', 'User', 'admin@example.com', '$2y$10$yiyr5RYPVwM59IjzgH3A2uASXwmQa2ogR2TTlGqPl3Nl1WPbjLPFa', 'ADMIN')
--ON CONFLICT (email) DO NOTHING;

-- Пример пользователя для тестов
-- Пароль 'password'
--INSERT INTO students (first_name, last_name, email, group_name, password_hash)
--VALUES ('Иван', 'Иванов', 'ivan@example.com', 'Группа 101', '$2a$12$yourbcryptgeneratedhashfor_password')
--ON CONFLICT (email) DO NOTHING;

--INSERT INTO courses (course_name, description)
--VALUES ('Математический анализ', 'Основы математического анализа для студентов первого курса.')
--ON CONFLICT (course_name) DO NOTHING;
