
The CA configuration here was created using the approach documented by RabbitMQ (https://www.rabbitmq.com/ssl.html#manual-certificate-generation) with three differences:
1. The openssl config file is in the dir above the ca dir (to make it easier to clear out the entire ca)
1. The ca dir is called ca, not testca
1. The passwords are always "password", not MyTestPassword.

This gives (starting in the ssl-server dir):
```
# Create the CA
mkdir ca
mkdir ca/certs ca/private
chmod 700 ca/private
echo 01 > ca/serial
touch ca/index.txt
cp openssl.cnf ca
cd ca
openssl req -x509 -config openssl.cnf -newkey rsa:2048 -days 365 \
    -out ca_certificate.pem -outform PEM -subj /CN=MyTestCA/ -nodes
openssl x509 -in ca_certificate.pem -out ca_certificate.cer -outform DER
cd ..

# Then create the server cert:
mkdir server
cd server
openssl genrsa -out private_key.pem 2048
openssl req -new -key private_key.pem -out req.pem -outform PEM \
    -subj /CN=$(hostname)/O=server/ -nodes
cd ../ca
openssl ca -config ../openssl.cnf -in ../server/req.pem -out \
    ../server/server_certificate.pem -notext -batch -extensions server_ca_extensions
cd ../server
openssl pkcs12 -export -out server_certificate.p12 -in server_certificate.pem -inkey private_key.pem \
    -passout pass:password
cd ..

# And the client cert:
mkdir client
cd client
openssl genrsa -out private_key.pem 2048
openssl req -new -key private_key.pem -out req.pem -outform PEM \
    -subj /CN=$(hostname)/O=client/ -nodes
cd ../ca
openssl ca -config ../openssl.cnf -in ../client/req.pem -out \
    ../client/client_certificate.pem -notext -batch -extensions client_ca_extensions
cd ../client
openssl pkcs12 -export -out client_certificate.p12 -in client_certificate.pem -inkey private_key.pem \
    -passout pass:password
cd ..

# Finally, put the server cert into a Java keystore:
keytool -import -alias server1 -file server/server_certificate.pem -keystore localhost-test-rabbit-store
```

Note that keystores are not backward compatible (i.e. if the keystore is created using JDK17 it may not be readable using JDK1.8).