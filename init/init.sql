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
VALUES (1, 'Cơm thố bò', 69000, 'RICE', 1),
       (2, 'Cơm gà xối mỡ', 65000, 'RICE', 1),
       (3, 'Cơm thố vịt quay', 69000, 'RICE', 1),
       (4, 'Cơm thố gà', 69000, 'RICE', 1),
       (5, 'Cơm thố ếch', 69000, 'RICE', 1),
       (6, 'Cơm chiên dương châu', 75000, 'RICE', 1),
       (7, 'Cơm chiên xá xíu', 65000, 'RICE', 1),
       (8, 'Cơm thố hải sản', 89000, 'RICE', 1),
       (9, 'Cơm thố nai', 89000, 'RICE', 1),
       (10, 'Mì hoành thánh xá xíu', 49000, 'NOODLE', 1),
       (11, 'Mì xíu', 49000, 'NOODLE', 1),
       (12, 'Mỳ vịt tiềm', 69000, 'NOODLE', 1),
       (13, 'Sủi cảo', 49000, 'DIMSUM', 1),
       (14, 'Hoành thánh', 49000, 'DIMSUM', 1),
       (15, 'Hoành thánh chiên', 49000, 'DIMSUM', 1),
       (16, 'Mì xào hải sản', 69000, 'NOODLE', 1),
       (17, 'Mì xào bò', 69000, 'NOODLE', 1),
       (18, 'Mì gà da giòn', 69000, 'NOODLE', 1),
       (19, 'Vịt quay 1/2 con', 180000, 'ROASTED', 1),
       (20, 'Vịt quay phần nhỏ', 69000, 'ROASTED', 1),
       (21, 'Vịt quay nguyên con', 360000, 'ROASTED', 1),
       (22, 'Gà quay phần nhỏ', 80000, 'ROASTED', 1),
       (23, 'Gà quay 1/2 con', 190000, 'ROASTED', 1),
       (24, 'Gà quay nguyên con', 380000, 'ROASTED', 1),
       (25, 'Canh rong biển', 15000, 'SOUP', 1),
       (26, 'Canh khổ qua', 15000, 'SOUP', 1),
       (27, 'Canh chua', 15000, 'SOUP', 1);
