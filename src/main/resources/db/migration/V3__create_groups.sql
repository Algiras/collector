CREATE TABLE groups (
    id UUID DEFAULT uuid_generate_v4 (),
    name TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE record_group (
    record_id UUID,
    group_id UUID,
    PRIMARY Key (record_id, group_id),
    CONSTRAINT fk_record
        FOREIGN KEY(record_id)
            REFERENCES records(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_group
        FOREIGN KEY(group_id)
            REFERENCES groups(id)
            ON DELETE CASCADE
);