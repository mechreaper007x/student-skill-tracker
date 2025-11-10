CREATE TABLE students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    leetcode_username VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    roles VARCHAR(255) NOT NULL DEFAULT 'ROLE_USER'
);

CREATE TABLE skill_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    problem_solving_score DOUBLE PRECISION,
    algorithms_score DOUBLE PRECISION,
    data_structures_score DOUBLE PRECISION,
    total_problems_solved INT,
    easy_problems INT,
    medium_problems INT,
    hard_problems INT,
    ranking INT,
    ai_advice CLOB,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (student_id) REFERENCES students(id)
);

CREATE TABLE password_reset_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    student_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (student_id) REFERENCES students(id)
);
