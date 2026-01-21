CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  email VARCHAR(100) NOT NULL,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  password VARCHAR(100) NOT NULL,
  username VARCHAR(50) NOT NULL
);

INSERT INTO users (email, first_name, last_name, password, username) VALUES
('ash@mail.com', 'Ash', 'Ketchum', 'pikachu123', 'ashketchum'),
('brock@mail.com', 'Brock', 'Harrison', 'onixrocks', 'brockstone'),
('misty@mail.com', 'Misty', 'Waterflower', 'togepi456', 'mistywaterflower');