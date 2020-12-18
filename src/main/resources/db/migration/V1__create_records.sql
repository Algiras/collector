CREATE TABLE records (
    id UUID DEFAULT uuid_generate_v4 (),
    name TEXT,
    link TEXT UNIQUE,
    price NUMERIC,
    PRIMARY KEY (id)
);