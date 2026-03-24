CREATE DATABASE IF NOT EXISTS wemeet
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE wemeet;

CREATE TABLE IF NOT EXISTS app_user (
  id VARCHAR(40) NOT NULL,
  login_id VARCHAR(60) NOT NULL,
  nickname VARCHAR(80) NOT NULL,
  email VARCHAR(120) NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  friend_code VARCHAR(40) NOT NULL,
  base_address VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_login_id (login_id),
  UNIQUE KEY uk_app_user_email (email),
  UNIQUE KEY uk_app_user_friend_code (friend_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS password_reset_token (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(40) NOT NULL,
  token_hash VARCHAR(80) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BIT(1) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_token_hash (token_hash),
  KEY idx_password_reset_token_user_id (user_id),
  CONSTRAINT fk_password_reset_token_user
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_relation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(40) NOT NULL,
  friend_user_id VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_friend_relation_user_friend (user_id, friend_user_id),
  KEY idx_friend_relation_friend_user_id (friend_user_id),
  CONSTRAINT fk_friend_relation_user
    FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_friend_relation_friend
    FOREIGN KEY (friend_user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS search_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(40) NOT NULL,
  query VARCHAR(255) NOT NULL,
  category VARCHAR(30) NOT NULL,
  searched_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_search_history_user_searched_at (user_id, searched_at),
  CONSTRAINT fk_search_history_user
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS meeting (
  id VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  meeting_date DATE NOT NULL,
  category VARCHAR(30) NOT NULL,
  host_user_id VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_meeting_host_user_id (host_user_id),
  CONSTRAINT fk_meeting_host_user
    FOREIGN KEY (host_user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS meeting_participant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  meeting_id VARCHAR(40) NOT NULL,
  user_id VARCHAR(40) NOT NULL,
  role VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_meeting_participant_meeting_user (meeting_id, user_id),
  KEY idx_meeting_participant_user_id (user_id),
  CONSTRAINT fk_meeting_participant_meeting
    FOREIGN KEY (meeting_id) REFERENCES meeting (id),
  CONSTRAINT fk_meeting_participant_user
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
