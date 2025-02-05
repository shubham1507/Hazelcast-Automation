//https://chat.qwenlm.ai/c/90ce947b-d0a7-4e26-9b8a-2a8685d069d4
pipeline {
    agent { label 'cm-linux' }

    stages {
        stage('DiagnosticCheck') {
            steps {
                script {
                    cleanWs()
                    deleteDir()

                    def userName
                    def password
                    def foldername = env.JOB_NAME.split('/')[env.JOB_NAME.split('/').length - 2]
                    def mapUpdated = false

                    // Clone Hazelcast-Services repository
                    git branch: "main", credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.sbi/dtc-hazelcast/Hazelcast-Services.git"
                    
                    sh("mkdir jqdir")
                    dir('jqdir') {
                        // Clone jq repository
                        git branch: "master", credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.sbi/sprintnet/jq.git"
                        sh """
                            chmod +x ./jq
                            mv ./jq ../
                        """
                    }

                    // Define parameters for LoggingType and HostName
                    [$class: 'ChoiceParameter',
                     choiceType: 'PT_SINGLE_SELECT',
                     filterLength: 1,
                     filterable: false,
                     description: 'Please choose Logging type',
                     name: 'LoggingType',
                     script: [
                         $class: 'GroovyScript',
                         fallbackScript: [],
                         classpath: [],
                         sandbox: true,
                         script: '''return ["jdk", "log4j2"]'''
                     ]
                    ],
                    [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter Server Name', name: 'HostName'],
                    [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter CR Number for Production Deployment', name: 'cr_number']
                    ])

                    // Load deployment script and initialize jq CLI
                    deployments = load "${WORKSPACE}/deployment.groovy"
                    jqcli = "${WORKSPACE}/jq"
                    environment = "${params.Environment}"

                    // Extract cluster list and safe URL using jq
                    Clusters_List = sh(script: """{ set +x; } 2>/dev/null; cat ${file} | ${jqcli} -r . '${environment}' """, returnStdout: true).trim()
                    Cluster_Safe_URL = sh(script: """{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqcli} -r . '${environment}' """, returnStdout: true).trim()

                    withCredentials([usernamePassword(credentialsId: 'sbiNET-G3-DEV-GITHUB-OAUTH', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        dir("${WORKSPACE}/${foldername}") {
                            sh """
                                echo "check current path"
                                echo "workspace:${WORKSPACE}"
                                foldername=$(pwd | grep -oE '[^/]+$')
                                git clone "https://$USERNAME:$PASSWORD@alm-github.systems.uk.sbi/dtc-hazelcast/${foldername}_config.git"
                                cd ${foldername}_config/${foldername}/${Environment}
                                git checkout main
                                echo "Cluster_Safe_URL ::::: ${Cluster_Safe_URL} ::::: cluster list :: ${Clusters_List}"
                            """
                        }
                    }

                    // Update XML files
                    updateXmlFiles("${WORKSPACE}/${foldername}/${foldername}_config/${foldername}/${environment}")

                    sh """
                        foldername=$(pwd | grep -oE '[^/]+$')
                        git checkout main
                        cd ${WORKSPACE}/${foldername}/${foldername}_config/${foldername}/${Environment}
                        echo "newfile:: hazelcast.xml"
                        cat hazelcast.xml
                        git config user.email "alm-github@sbi.com"
                        git config user.name "sbiNET-G3-DEV"
                        git status
                        git add .
                        git commit -m "added with Automation via Ansible with IDP or JENKINS"
                        git push origin main
                    """
                }
            }
        }
    }

    try {
        def templateId = "163305"
        def environment = "${params.Environment}"
        def containers = "container01"
        def loggingtype = "${params.LoggingType}"
        def extravars = "{\"hostname\": \"${hostname}\", \"loggingType\": \"${loggingtype}\", \"cluster\": \"${Clusters_List}\", \"container\": \"${containers}\", \"env\": \"${environment}\", \"cluster_safe_url\": \"${Cluster_Safe_URL}\", \"foldername\": \"${foldername}\"}"
        def extravarsApproval = "{\"hostname\":\"${hostname}\", \"cluster_safe_url\": \"${Cluster_Safe_URL}\", \"cluster\": \"${Clusters_List}\", \"container\": \"${containers}\", \"env\": \"${environment}\", \"foldername\":\"${foldername}\"}"

        if (environment.toLowerCase().startsWith("prod")) {
            timeout(time: 120, unit: 'SECONDS') {
                def userInput = input(
                    id: 'Input-username',
                    parameters: [
                        [$class: 'StringParameterDefinition', defaultValue: '', description: 'Enter Username:', name: 'Username'],
                        [$class: 'hudson.model.PasswordParameterDefinition', description: 'Enter Password:', name: 'Password']
                    ],
                    submitterParameter: 'approver'
                )
                userName = userInput['Username']
                password = userInput['Password'].toString()
            }

            def templateIdApproval = "30931"
            def cr_number = "${params.cr_number}"

            if (cr_number == "") {
                error "Error! CR Number is Empty for Production Environment."
            }

            extravars = "{\"cr_number\": \"${params.cr_number}\", \"hostname\":\"${Inventory}\", \"cluster\": \"${Clusters_List}\", \"container\": \"${containers}\", \"env\": \"${environment}\", \"cluster_safe_url\": \"${Cluster_Safe_URL}\", \"foldername\": \"${foldername}\"}"
            extravarsApproval = "{\"cr_number\": \"${params.cr_number}\", \"hostname\":\"${Inventory}\", \"cluster_safe_url\": \"${Cluster_Safe_URL}\", \"cluster\": \"${Clusters_List}\", \"container\": \"${containers}\", \"env\": \"${environment}\", \"foldername\": \"${foldername}\"}"

            println("Extra-Vars are: " + extravars)
            deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)
        }
    } catch (e) {
        withCredentials([usernamePassword(credentialsId: 'sbiNET-G3-DEV-GITHUB-OAUTH', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            dir("${WORKSPACE}/${foldername}") {
                sh """
                    pwd
                    foldername=$(pwd | grep -oE '[^/]+$')
                    git checkout main
                    cd ${WORKSPACE}/${foldername}/${foldername}_config/${foldername}/${Environment}
                    git checkout main
                    git config user.email "snj@.sbi.com"
                    git config --global user.email "sbi-github@sbi.com"
                    git config user.name "sbiNET-G3-DEV"
                    git revert HEAD
                    git status
                    cat hazelcast.xml
                    git push https://$USERNAME:$PASSWORD@alm-github.systems.uk.sbi/dtc-hazelcast/${foldername}_config.git main
                """
                error "Pipeline to onboard map failed. Please check the input you have provided."
            }
        }
    }
}

def updateXmlFiles(basePath) {
    println("Workspace location: ${env.WORKSPACE}")
    println("Listing all files in the basePath: ${basePath}")
    sh "ls -l ${basePath}"

    def hazelcastConfigFilePath = "${basePath}/hazelcast.xml"
    println("Updating: ${hazelcastConfigFilePath}")

    if (fileExists(hazelcastConfigFilePath)) {
        def hazelcastConfigContent = readFile(hazelcastConfigFilePath)
        println("Original content of hazelcast.xml:\n${hazelcastConfigContent}")

        def loggingType = params.LoggingType
        def loggingProperty = "<property>${loggingType}</property>"

        if (hazelcastConfigContent.contains(loggingProperty)) {
            println("hazelcast.xml already has the logging property: ${loggingProperty}")
        } else {
            if (loggingType == 'jdk') {
                hazelcastConfigContent = hazelcastConfigContent.replaceAll(/.*<\/property>/, 'jdk')
            } else if (loggingType == 'log4j2') {
                hazelcastConfigContent = hazelcastConfigContent.replaceAll(/.*<\/property>/, 'log4j2')

                // Add diagnostics properties for log4j2
                hazelcastConfigContent = hazelcastConfigContent.replace(
                    'log4j2', 
                    'log4j2\n' +
                    'true\n' +
                    '/opt/HZ/diagnostics\n' +
                    'info\n' +
                    '10\n' +
                    '30\n' +
                    '30\n' +
                    '60\n' +
                    'LOGGER'
                )
            } else {
                error "Invalid Logging type: ${loggingType}. Must be 'jdk' or 'log4j2'."
            }

            println("New content for hazelcast.xml:\n${hazelcastConfigContent}")
            writeFile(file: hazelcastConfigFilePath, text: hazelcastConfigContent)
            println("Finished writing new content to hazelcast.xml")
        }
    } else {
        println("File not found: ${hazelcastConfigFilePath}")
    }

    def startContainerFilePath = "${basePath}/startContainer.sh"
    println("Updating: ${startContainerFilePath}")

    if (fileExists(startContainerFilePath)) {
        def startContainerContent = readFile(startContainerFilePath)
        println("Original content of startContainer.sh:\n${startContainerContent}")

        def loggingType = params.LoggingType

        if (loggingType == 'jdk') {
            echo "jdk logging"
        } else if (loggingType == 'log4j2') {
            // Append classPath for log4j2
            startContainerContent = startContainerContent.replace(
                'export classpath="$HZ_ROOT/$sName/lib/hazelcast-enterprise-all.jar:$deploymentArea/hz_extensions.jar:',
                'export classpath="$HZ_ROOT/$sName/lib/hazelcast-enterprise-all.jar:$deploymentArea/hz_extensions.jar:$HZ_ROOT/commonlib/log4j-api.jar:$HZ_ROOT/commonlib/log4j-core.jar:'
            )

            // Add logging configuration directly after $appdOpts in javaOptsFull
            startContainerContent = startContainerContent.replace(
                'javaOptsFull="$javaOpts $srvJavaOpts $appdOpts',
                'javaOptsFull="$javaOpts $srvJavaOpts $appdOpts -Dlog4j.configurationFile=$HZ_ROOT/$sName/etc/log4j2.xml'
            )
        } else {
            error "Invalid Logging type: ${loggingType}. Must be 'jdk' or 'log4j2'."
        }

        println("New content for startContainer.sh:\n${startContainerContent}")
        writeFile(file: startContainerFilePath, text: startContainerContent)
        println("Finished writing new content to startContainer.sh")
    } else {
        println("File not found: ${startContainerFilePath}")
    }

    return true
}
