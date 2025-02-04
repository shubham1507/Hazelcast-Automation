pipeline {
    agent { 
        label 'cm-linux' 
    }
    
    stages {
        stage('Checkout Repositories') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    
                    def userName
                    def password
                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]
                    
                    // Checkout SSL Configuration Repository
                    git branch: "main",
                        credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH",
                        url: "https://alm-github.systems.uk.SBI/security/SSL-Config.git"
                    
                    // Checkout jq utility repository
                    sh("mkdir jqdir")
                    dir('jqdir') {
                        git branch: "master",
                            credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH",
                            url: "https://alm-github.systems.uk.SBI/tools/jq.git"
                    }
                    
                    sh "chmod +x ./jq"
                    sh "mv ./jq ../"
                    
                    // Process environment configurations
                    dir("${workspace}/${foldername}/") {
                        sh "{ set +x; } 2>/dev/null;"
                        sh "cat Environments.groovy >> env.txt"
                        def envList = readFile 'env.txt'
                        
                        properties([
                            parameters([
                                [
                                    $class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    name: 'Environment',
                                    script: [
                                        $class: 'GroovyScript',
                                        script: "${envList}"
                                    ]
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Enter CR Number for Production Deployment',
                                    name: 'cr_number'
                                ]
                            ])
                        ])
                    }
                }
            }
        }

        stage('Generate Keystore and CSR') {
            steps {
                script {
                    sh '''
                    keytool -genkey -alias devecbhz -keyalg rsa -keysize 2048 -sigalg SHA256withRSA \
                    -ext "EKU=serverAuth, clientAuth" -dname "CN=ecbdrndev-wdc.hc.cloud.uk.hsbc, OU=CMB, O=HSBC Holdings plc, L=London, C=GB" \
                    -ext "san=dns:ecbdrndev-wdc.hc.cloud.uk.hsbc, dns:ecbdrnsit-wdc.hc.cloud.uk.hsbc" \
                    -keystore keystore.jks -storepass changeit

                    keytool -certreq -alias devecbhz -keystore keystore.jks -storepass changeit -file devecbhz.csr
                    '''
                }
            }
        }

        stage('SSL Certificate Import') {
            steps {
                script {
                    sh '''
                    keytool -import -trustcacerts -keystore keystore.jks -alias devecbhz -file ecbdrndev-wdc.hc.cloud.uk.hsbc.cer -storepass changeit
                    keytool -import -trustcacerts -keystore keystore.jks -file "Root.cer" -alias hsbc_root_ca -storepass changeit
                    keytool -import -trustcacerts -keystore keystore.jks -file "Int.cer" -alias hsbc_issuing_ca02 -storepass changeit
                    '''
                }
            }
        }

        stage('Verify SSL Configuration') {
            steps {
                script {
                    sh '''
                    keytool -list -keystore keystore.jks -v | grep "Alias name\\|Entry type:"
                    keytool -list -keystore keystore.jks -v
                    '''
                }
            }
        }
    }
}
