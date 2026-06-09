# SSL Certificates for RabbitMQ Tests

This directory contains SSL certificates for testing RabbitMQ SSL/TLS connections with peer validation.

## Regenerating Certificates

To regenerate certificates:

```bash
cd src/test/resources/ssl-server
./generate-certs.sh
```

The script will:
- Create a self-signed CA
- Generate server and client certificates
- Create a Java keystore
- All certificates valid for 20 years

All keystores use password: `password`

⚠️ **For testing only** - Do not use in production.
