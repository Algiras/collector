CREATE TABLE users (
    id UUID DEFAULT uuid_generate_v4 (),
    email TEXT UNIQUE,
    pass_hash TEXT,
    access_role TEXT,
    PRIMARY KEY (id)
);