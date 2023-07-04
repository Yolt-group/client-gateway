CREATE TABLE IF NOT EXISTS client_proxy.client_user_profile(
    client_id uuid,
    profile_id uuid,
    user_id uuid,
    PRIMARY KEY (client_id, profile_id)
);