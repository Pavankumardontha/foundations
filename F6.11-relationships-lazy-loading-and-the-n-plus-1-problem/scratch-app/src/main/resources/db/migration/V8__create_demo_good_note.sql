-- F6.11 Lesson 19: the counterpart to demo_note, but with a CLIENT-ASSIGNED uuid key.
-- The application sets the id (in the constructor) before the row is ever saved, so the
-- key is stable from birth - exactly what equals()/hashCode() need.
CREATE TABLE demo_good_note (
    id   uuid PRIMARY KEY,
    body text NOT NULL
);
