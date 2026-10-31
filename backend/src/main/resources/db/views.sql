CREATE OR REPLACE VIEW v_customers AS SELECT id, raw_json FROM app_users;
CREATE OR REPLACE VIEW v_subscriptions AS SELECT id, customer_id, raw_json FROM app_subscriptions;
CREATE OR REPLACE VIEW v_payments AS SELECT id, customer_id, raw_json FROM app_payments;
CREATE OR REPLACE VIEW v_usage AS SELECT customer_id, raw_json FROM app_usage;
