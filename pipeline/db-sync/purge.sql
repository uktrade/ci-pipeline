DO $$
DECLARE u text;
BEGIN
  SELECT rolname INTO u FROM pg_roles WHERE rolname ~ current_database();
  EXECUTE 'DROP OWNED BY "' || u || '" CASCADE';
END $$;
