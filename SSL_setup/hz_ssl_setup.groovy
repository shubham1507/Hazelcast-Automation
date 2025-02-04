pipeline {
    agent { 
        label 'cm-linux' 
    }
    
    environment {
        ANSIBLE_PLAYBOOK = "/path/to/ssl_setup.yml"
        ANSIBLE_INVENTORY = "/path/to/inventory"
    }

    stages {
        stage('SSL-Setup-Initialization') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    
                    // Creating necessary directories for SSL setup
                    sh "mkdir -p /opt/HZ"
                    sh "touch /opt/HZ/Root.cer"
                    sh "touch /opt/HZ/Int.cer"
                    sh "touch /opt/HZ/devecbhz.csr"
                    
                    // Example placeholder for the Keystore file
                    sh "touch /opt/HZ/keystore.jks"
                }
            }
        }

        stage('Generate Keystore') {
            steps {
                script {
                    // Define necessary variables
                    def keystorePassword = "changeit"
                    def keystorePath = "/opt/HZ/keystore.jks"
                    def keystoreAlias = "devecbhz"
                    def csrFilePath = "/opt/HZ/devecbhz.csr"
                    
                    // Generate the Keystore.jks
                    sh """
                        keytool -genkey -alias ${keystoreAlias} -keyalg rsa -keysize 2048 -sigalg SHA256withRSA \
                        -ext "EKU=serverAuth, clientAuth" -dname "CN=ecbdrndev-wdc.hc.cloud.uk.hsbc, OU=CMB, O=HSBC Holdings plc, L=London, C=GB" \
                        -ext "san=dns:ecbdrndev-wdc.hc.cloud.uk.hsbc, dns: ecbdrnsit-wdc.hc.cloud.uk.hsbc, dns: gb125172940.hc.cloud.uk.hsbc, dns: gb125172951.hc.cloud.uk.hsbc" \
                        -keystore ${keystorePath} -storepass ${keystorePassword}
                    """
                }
            }
        }

        stage('Generate CSR') {
            steps {
                script {
                    // Generate the CSR from the Keystore
                    def keystorePassword = "changeit"
                    def csrFilePath = "/opt/HZ/devecbhz.csr"
                    sh """
                        keytool -certreq -alias devecbhz -keystore /opt/HZ/keystore.jks -storepass ${keystorePassword} -file ${csrFilePath}
                    """
                }
            }
        }

        stage('Import Certificates') {
            steps {
                script {
                    // Import Root Certificate into the Keystore
                    def keystorePassword = "changeit"
                    def rootCertificate = "/opt/HZ/Root.cer"
                    def intermediateCertificate = "/opt/HZ/Int.cer"
                    def keystorePath = "/opt/HZ/keystore.jks"
                    def certFile = "/opt/HZ/devecbhz.cer"
                    
                    // Import Root and Intermediate Certificates into the Keystore
                    sh """
                        keytool -import -trustcacerts -keystore ${keystorePath} -file ${rootCertificate} -alias hsbc_root_ca -storepass ${keystorePassword}
                        keytool -import -trustcacerts -keystore ${keystorePath} -file ${intermediateCertificate} -alias hsbc_issuing_ca02 -storepass ${keystorePassword}
                        keytool -import -trustcacerts -keystore ${keystorePath} -alias devecbhz -file ${certFile} -storepass ${keystorePassword}
                    """
                }
            }
        }

        stage('Verify Keystore') {
            steps {
                script {
                    // Verify Keystore entries and details
                    def keystorePassword = "changeit"
                    def keystorePath = "/opt/HZ/keystore.jks"
                    
                    // Verify the entries in the Keystore
                    sh """
                        keytool -list -v -keystore ${keystorePath} -storepass ${keystorePassword} | grep "Alias name\| Entry type:"
                        keytool -list -keystore ${keystorePath} -v -storepass ${keystorePassword}
                    """
                }
            }
        }

        stage('Ansible SSL Playbook Execution') {
            steps {
                script {
                    def extraVars = [
                        keystore_path: "/opt/HZ/keystore.jks",
                        keystore_password: "changeit",
                        csr_file: "/opt/HZ/devecbhz.csr",
                        root_certificate: "/opt/HZ/Root.cer",
                        intermediate_certificate: "/opt/HZ/Int.cer",
                        cert_file: "/opt/HZ/devecbhz.cer"
                    ]
                    
                    // Call the Ansible playbook for SSL setup
                    sh """
                        ansible-playbook -i ${ANSIBLE_INVENTORY} ${ANSIBLE_PLAYBOOK} --extra-vars "${extraVars}"
                    """
                }
            }
        }
    }
}
