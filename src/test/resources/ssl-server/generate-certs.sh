#!/bin/bash
set -e

# Configuration
DAYS=7300  # 20 years
PASSWORD="password"
HOSTNAME="test-rabbitmq-server"

echo "=== Cleaning up old certificates ==="
rm -rf ca server client localhost-test-rabbit-store
mkdir -p ca/certs ca/private server client
chmod 700 ca/private

echo "=== Creating CA (Certificate Authority) ==="
echo 01 > ca/serial
touch ca/index.txt
cp openssl.cnf ca/

cd ca
openssl req -x509 -config openssl.cnf -newkey rsa:2048 -days ${DAYS} \
    -out ca_certificate.pem -outform PEM \
    -subj /CN=MyTestCA/ -nodes \
    -keyout private/ca_private_key.pem

openssl x509 -in ca_certificate.pem -out ca_certificate.cer -outform DER
cd ..

echo "=== Creating Server Certificate ==="
cd server
openssl genrsa -out private_key.pem 2048

openssl req -new -key private_key.pem -out req.pem -outform PEM \
    -subj /CN=${HOSTNAME}/O=server/ -nodes

cd ../ca
openssl ca -config openssl.cnf -in ../server/req.pem \
    -out ../server/server_certificate.pem -notext -batch \
    -extensions server_ca_extensions -days ${DAYS}

cd ../server
openssl pkcs12 -export -out server_certificate.p12 \
    -in server_certificate.pem -inkey private_key.pem \
    -passout pass:${PASSWORD}
cd ..

echo "=== Creating Client Certificate ==="
cd client
openssl genrsa -out private_key.pem 2048

openssl req -new -key private_key.pem -out req.pem -outform PEM \
    -subj /CN=${HOSTNAME}/O=client/ -nodes

cd ../ca
openssl ca -config openssl.cnf -in ../client/req.pem \
    -out ../client/client_certificate.pem -notext -batch \
    -extensions client_ca_extensions -days ${DAYS}

cd ../client
openssl pkcs12 -export -out client_certificate.p12 \
    -in client_certificate.pem -inkey private_key.pem \
    -passout pass:${PASSWORD}
cd ..

echo "=== Creating Java Keystore ==="
keytool -import -alias server1 -file server/server_certificate.pem \
    -keystore localhost-test-rabbit-store -storepass ${PASSWORD} \
    -noprompt

echo "=== Certificate Generation Complete ==="
echo "All certificates valid for ${DAYS} days (20 years)"
echo "Password for all keystores: ${PASSWORD}"

# Verify certificates
echo ""
echo "=== Certificate Expiration Dates ==="
echo "CA Certificate:"
openssl x509 -in ca/ca_certificate.pem -noout -dates
echo ""
echo "Server Certificate:"
openssl x509 -in server/server_certificate.pem -noout -dates
echo ""
echo "Client Certificate:"
openssl x509 -in client/client_certificate.pem -noout -dates
