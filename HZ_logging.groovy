// Jenkinsfile for Hazelcast Logging Configuration Pipeline
//https://claude.ai/chat/ed8fc0bc-1604-45a4-8e5c-3be32b448c77
pipeline {
    agent { label 'cm-linux' }
    
    environment {
        HZ_ROOT = '/opt/HZ'
        LOG4J_VERSION = '2.21.0'
        COMMON_LIB = "${HZ_ROOT}/commonlib"
    }
    
    stages {
        stage('Initialize') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    
                    // Extract folder name from workspace path
                    def folderParts = pwd().split("/")
                    def folderName = folderParts[folderParts.length - 2]
                    
                    // Clone required repositories
                    git branch: "main", 
                        credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", 
                        url: "https://alm-github.systems.uk.sbi/dtc-hazelcast/Hazelcast-Services.git"
                    
                    // Setup environment configuration
                    dir("${WORKSPACE}/${folderName}/") {
                        sh "cat Environments.groovy >> env.txt"
                        def envList = readFile('env.txt')
                        
                        properties([
                            parameters([
                                choice(name: 'Environment', choices: envList, description: 'Select Environment'),
                                choice(name: 'ACTION', choices: ['setup', 'switch'], description: 'Action to perform'),
                                choice(name: 'LOGGING_TYPE', choices: ['log4j2', 'jdk'], description: 'Logging system to use'),
                                string(name: 'HostName', defaultValue: '', description: 'Enter Server Name'),
                                string(name: 'cr_number', defaultValue: '', description: 'Enter CR Number for Production Deployment')
                            ])
                        ])
                    }
                }
            }
        }
        
        stage('Prepare Configuration') {
            steps {
                script {
                    // Validate parameters
                    if (params.HostName.trim() == "") {
                        error "Error! Host_Name is Empty. Please enter a valid Host_Name value."
                    }
                    
                    // Load deployment configurations
                    def deployments = load "${WORKSPACE}/deployment.groovy"
                    def templateId = params.Environment.toLowerCase().startsWith("prod") ? "64468" : "110777"
                    def containers = "container01"
                    
                    // Prepare logging configurations
                    writeFile file: "${WORKSPACE}/log4j2.xml", text: '''<?xml version="1.0" encoding="UTF-8"?>
                        <Configuration status="INFO">
                            <Appenders>
                                <RollingFile name="LogToRollingFile" 
                                            fileName="/opt/HZ/diagnostics/diagnostics.log"
                                            filePattern="/opt/HZ/diagnostics/diagnostics-%i.log">
                                    <PatternLayout>
                                        <Pattern>%d{ISO8601} %-5p [%c{1}] %m%n</Pattern>
                                    </PatternLayout>
                                    <Policies>
                                        <SizeBasedTriggeringPolicy size="10 MB"/>
                                    </Policies>
                                    <DefaultRolloverStrategy max="10"/>
                                </RollingFile>
                            </Appenders>
                            <Loggers>
                                <Logger name="com.hazelcast.diagnostics" level="debug" additivity="false">
                                    <AppenderRef ref="LogToRollingFile"/>
                                </Logger>
                            </Loggers>
                        </Configuration>'''
                        
                    writeFile file: "${WORKSPACE}/jdk-logging.properties", text: '''handlers=java.util.logging.FileHandler
                        .level=INFO
                        java.util.logging.FileHandler.pattern=/opt/HZ/diagnostics/hazelcast.log
                        java.util.logging.FileHandler.limit=10485760
                        java.util.logging.FileHandler.count=10
                        java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
                        java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %2$s: %5$s%6$s%n'''
                    
                    // Prepare extra vars for Ansible
                    def extravars = [
                        hostname: params.HostName,
                        action: params.ACTION,
                        logging_type: params.LOGGING_TYPE,
                        hz_root: env.HZ_ROOT,
                        log4j_version: env.LOG4J_VERSION,
                        container: containers,
                        env: params.Environment
                    ]
                    
                    // Handle production environment
                    if (params.Environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(
                                id: 'Input-username',
                                parameters: [
                                    string(name: 'Username', description: 'Enter Username:'),
                                    password(name: 'Password', description: 'Enter Password:')
                                ],
                                submitterParameter: 'approver'
                            )
                            
                            if (params.cr_number.trim() == "") {
                                error "Error! CR Number is Empty for Production Environment."
                            }
                            
                            extravars.cr_number = params.cr_number
                        }
                    }
                    
                    // Get deployment approval and trigger Ansible
                    deployments.getApproval("Approve: To execute logging configuration deployment", "HZ-approvers")
                    deployments.triggerAnsibleTower(templateId, params.Environment, groovy.json.JsonOutput.toJson(extravars))
                }
            }
        }
        
        stage('Verify Configuration') {
            steps {
                script {
                    // Run health check or verification steps
                    def verificationCommand = params.LOGGING_TYPE == 'log4j2' ? 
                        "grep 'log4j2' ${HZ_ROOT}/*/hazelcast.xml" :
                        "grep 'jdk' ${HZ_ROOT}/*/hazelcast.xml"
                        
                    sshagent(['hazelcast-ssh-key']) {
                        sh "ssh ${params.HostName} '${verificationCommand}'"
                    }
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
        success {
            echo "Hazelcast logging configuration completed successfully"
        }
        failure {
            echo "Pipeline failed! Please check the logs for details"
        }
    }
}
