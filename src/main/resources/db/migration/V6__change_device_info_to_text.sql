-- V6: Change device_info from JSONB to TEXT
-- deviceInfo is a plain descriptive string (e.g. "Windows", "PostmanRuntime"),
-- not structured JSON. Storing it as JSONB causes:
--   ERROR: invalid input syntax for type json
--   Detail: Token "Windows" is invalid.
ALTER TABLE refresh_tokens
    ALTER COLUMN device_info TYPE TEXT;
