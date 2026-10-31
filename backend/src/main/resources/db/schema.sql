-- Synthetic demo data; integration schemas approximate published API objects, not live integrations.
-- Internal identity mapping; application-owned, no external source.
CREATE TABLE IF NOT EXISTS app_users (id VARCHAR(128) PRIMARY KEY, name TEXT, country TEXT, language TEXT, quiz_goal TEXT, card_fingerprint TEXT, offers_12m INTEGER, last_offer_at TEXT, contacts_last_30d INTEGER, acquisition_channel TEXT, ltv_usd DECIMAL(14,2), raw_json TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS app_usage (customer_id VARCHAR(128) PRIMARY KEY, sessions_since_last_charge INTEGER, last_active_at TEXT, streak_days INTEGER, cancel_flow_abandoned INTEGER, raw_json TEXT NOT NULL);
-- Stripe customer: https://docs.stripe.com/api/customers/object
CREATE TABLE IF NOT EXISTS stripe_customers (id VARCHAR(128) PRIMARY KEY, email TEXT, created TEXT, metadata_app_user_id VARCHAR(128));
-- Stripe subscription: https://docs.stripe.com/api/subscriptions/object
CREATE TABLE IF NOT EXISTS stripe_subscriptions (id VARCHAR(128) PRIMARY KEY, customer VARCHAR(128), status TEXT, current_period_start TEXT, current_period_end TEXT, cancel_at_period_end BOOLEAN, canceled_at TEXT, price_id TEXT, unit_amount BIGINT, billing_interval TEXT, discount_percent INTEGER, pause_collection_resumes_at TEXT);
-- Stripe charge: https://docs.stripe.com/api/charges/object
CREATE TABLE IF NOT EXISTS stripe_charges (id VARCHAR(128) PRIMARY KEY, customer VARCHAR(128), amount BIGINT, currency TEXT, status TEXT, created TEXT, payment_method_fingerprint TEXT, invoice TEXT, description TEXT, refunded BOOLEAN, amount_refunded BIGINT, disputed BOOLEAN);
-- Stripe refund: https://docs.stripe.com/api/refunds/object
CREATE TABLE IF NOT EXISTS stripe_refunds (id VARCHAR(255) PRIMARY KEY, charge VARCHAR(128), amount BIGINT, status TEXT, created TEXT, reason TEXT);
-- Stripe dispute: https://docs.stripe.com/api/disputes/object
CREATE TABLE IF NOT EXISTS stripe_disputes (id VARCHAR(255) PRIMARY KEY, charge VARCHAR(128), amount BIGINT, status TEXT, reason TEXT, created TEXT);
-- App Store decoded transaction: https://developer.apple.com/documentation/appstoreservernotifications/jwstransactiondecodedpayload
CREATE TABLE IF NOT EXISTS appstore_transactions (transaction_id VARCHAR(128) PRIMARY KEY, original_transaction_id VARCHAR(128), product_id TEXT, purchase_date TEXT, expires_date TEXT, revocation_date TEXT, app_account_token TEXT);
-- App Store Server Notifications V2: https://developer.apple.com/documentation/appstoreservernotifications/responsebodyv2decodedpayload
CREATE TABLE IF NOT EXISTS appstore_notifications (notification_uuid VARCHAR(128) PRIMARY KEY, notification_type TEXT, subtype TEXT, original_transaction_id VARCHAR(128), signed_date TEXT);
-- Play subscription purchase: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2
CREATE TABLE IF NOT EXISTS play_subscriptions (purchase_token VARCHAR(128) PRIMARY KEY, subscription_state TEXT, line_item_product_id TEXT, expiry_time TEXT, linked_purchase_token TEXT, obfuscated_external_account_id TEXT);
-- RevenueCat subscriber and entitlements: https://www.revenuecat.com/docs/api-v1
CREATE TABLE IF NOT EXISTS rc_subscribers (app_user_id VARCHAR(128) PRIMARY KEY, original_app_user_id VARCHAR(128));
CREATE TABLE IF NOT EXISTS rc_entitlements (id VARCHAR(128) PRIMARY KEY, app_user_id VARCHAR(128), entitlement_id TEXT, store TEXT, expires_date TEXT, period_type TEXT, unsubscribe_detected_at TEXT);
-- Zendesk tickets: https://developer.zendesk.com/api-reference/ticketing/tickets/tickets/
CREATE TABLE IF NOT EXISTS zendesk_tickets (id VARCHAR(128) PRIMARY KEY, requester_id VARCHAR(128), subject TEXT, description TEXT, status TEXT, priority TEXT, tags TEXT, created_at TEXT, channel TEXT, raw_json TEXT NOT NULL);
-- AppsFlyer raw installs: https://support.appsflyer.com/hc/en-us/articles/208387843
CREATE TABLE IF NOT EXISTS appsflyer_installs (appsflyer_id VARCHAR(128) PRIMARY KEY, customer_user_id VARCHAR(128), media_source TEXT, campaign TEXT, install_time TEXT, platform TEXT);
-- Demo projections retain original source JSON to avoid silently dropping domain fields.
CREATE TABLE IF NOT EXISTS app_subscriptions (id VARCHAR(128) PRIMARY KEY, customer_id VARCHAR(128), processor TEXT, status TEXT, plan TEXT, price DECIMAL(14,2), raw_json TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS app_payments (id VARCHAR(128) PRIMARY KEY, customer_id VARCHAR(128), subscription_id VARCHAR(128), processor TEXT, status TEXT, amount DECIMAL(14,2), created_at TEXT, raw_json TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS app_history (id VARCHAR(128) PRIMARY KEY, customer_id VARCHAR(128), root_cause TEXT, refund_amount DECIMAL(14,2));
