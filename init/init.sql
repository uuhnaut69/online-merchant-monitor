CREATE TABLE IF NOT EXISTS restaurants
(
    id   BIGINT PRIMARY KEY NOT NULL,
    name TEXT               NOT NULL
);

CREATE TABLE IF NOT EXISTS dishes
(
    id            BIGINT PRIMARY KEY NOT NULL,
    name          TEXT               NOT NULL,
    price         NUMERIC(19, 2)     NOT NULL,
    type          VARCHAR(255)       NOT NULL,
    restaurant_id BIGINT             NOT NULL references restaurants (id) ON DELETE CASCADE
);

INSERT INTO restaurants(id, name)
VALUES (1, 'RESTAURANT_A');

INSERT INTO dishes(id, name, price, type, restaurant_id)
VALUES (1, 'Grilled oyster with onion oil', 69000, 'SEAFOOD', 1),
       (2, 'Grilled octopus', 65000, 'SEAFOOD', 1),
       (3, 'Grilled lobster', 2650000, 'SEAFOOD', 1),
       (4, 'Fish salad', 69000, 'SEAFOOD', 1),
       (5, 'Roasted duck', 360000, 'ROASTED', 1),
       (6, 'Roasted chicken', 380000, 'ROASTED', 1),
       (7, 'Roasted pork meat', 180000, 'ROASTED', 1),
       (8, 'Seaweed soup', 15000, 'SOUP', 1),
       (9, 'Sour soup', 15000, 'SOUP', 1),
       (10, 'Crab soup', 15000, 'SOUP', 1);
