ALTER TABLE users ADD COLUMN online_status_visibility TEXT NOT NULL DEFAULT 'same_as_last_seen';
ALTER TABLE users ADD COLUMN profile_photo_visibility TEXT NOT NULL DEFAULT 'everyone';
ALTER TABLE users ADD COLUMN phone_number_visibility TEXT NOT NULL DEFAULT 'nobody';
ALTER TABLE users ADD COLUMN read_receipts_enabled INTEGER NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN who_can_message_me TEXT NOT NULL DEFAULT 'everyone';
ALTER TABLE users ADD COLUMN who_can_call_me TEXT NOT NULL DEFAULT 'everyone';
ALTER TABLE users ADD COLUMN who_can_add_me_to_groups TEXT NOT NULL DEFAULT 'everyone';
