# Teller Enrollment Update

Minimal static page for updating a Teller enrollment via Teller Connect.

## Usage

1. Set environment variables:

   ```bash
   export TELLER_APP_ID=app_12345
   export TELLER_ENROLLMENT_ID=enr_12345
   # optional (defaults to sandbox)
   export TELLER_ENV=sandbox
   ```

2. Generate `env.js`:

   ```bash
   node generate-env.js
   ```

3. Serve the directory and open `index.html`:

   ```bash
   python3 -m http.server 8000
   # then open http://localhost:8000/index.html
   ```

4. Click **Update Enrollment** and complete the Teller Connect flow. The new enrollment ID is logged to the page and console.
