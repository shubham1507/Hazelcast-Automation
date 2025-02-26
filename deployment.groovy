import hudson.tasks.Mailer;
def triggerAnsibleTower(String templateId, String environment, String extraVars, String userName, String password) {
    def ansibleUser = ""
    def ansiblePass = ""
    if(params.EXTRA_VARS != null) {
        extraVars = EXTRA_VARS ?: extraVars
    }
    if(environment.toLowerCase() != "prod") {
        detailURL = "https://alm-aapuat-sdc.hc.cloud.uk.hsbc/api/v2"
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "771dc67d-9fd7-482b-86da-ea92414d985c", passwordVariable: 'USERPASS', usernameVariable: 'USERNAME']]) {
            ansibleUser = env.USERNAME
            ansiblePass = env.USERPASS
        }
    } else {
        detailURL = "https://ansiblecentral.hc.cloud.uk.hsbc/api/v2"
        ansibleUser = "${userName}"
        ansiblePass = "${password}"
    }
    launchURL = "${detailURL}/job_templates/${templateId}/launch/"
    statusURL = "${detailURL}/jobs"
    dir("Ansible") {
        if(!extraVars?.trim()) {
            println ("Ansible extra-vars not found!")
            sh"""{ set +x; } 2>/dev/null; curl -sS -k -o JobID-out.json -u '${ansibleUser}':'${ansiblePass}' -H 'Content-Type: application/json' -X POST "${launchURL}"| "${WORKSPACE}/jq" -r '.' > /dev/null 2>&1"""
        } else {
            sh"""{ set +x; } 2>/dev/null; curl -sS -k -o JobID-out.json -u '${ansibleUser}':'${ansiblePass}' -H 'Content-Type: application/json' -X POST -d '{"extra_vars": ${extraVars} }' "${launchURL}"| "${WORKSPACE}/jq" -r '.' > /dev/null 2>&1"""
        }

        jobID = sh(script: """{ set +x; } 2>/dev/null; cat JobID-out.json | "${WORKSPACE}/jq" -r '.id'""",returnStdout: true).trim()
        jobURL = "${statusURL}/${jobID}"

        if (jobID == 'null') {
            println("ERROR! JobId for templateId ${templateId} is NULL. Please verify templateId and extra-vars.")
            sh """ { set +x; } 2>/dev/null; exit 1 """
        } else {
            println("JobId - ${jobID}")

            def ansibleStdout = sh(script: """
                { set +x; } 2>/dev/null;
                jobStatus="pending"
                echo "Ansible job execution is in progress..."
                echo " "
                while [ "\$jobStatus" != "successful" -a "\$jobStatus" != "failed" ]
                do
                    jobStatusResponse=`curl -sS -k -u '${ansibleUser}':'${ansiblePass}' -H 'Content-Type: application/json' -X GET  "${statusURL}/${jobID}/"`
                    jobStatus=`echo \$jobStatusResponse | "${WORKSPACE}/jq" -r '.status'`
                    if [ "\$jobStatus" == "successful" -a "\$jobStatus" == "failed" ]
                    then
                        break
                    fi
                    sleep 20
                done

                echo "********************************************************************************\nStart : Ansible Job Output\n********************************************************************************\n"
                curl -sS -k -u '${ansibleUser}':'${ansiblePass}' -H 'Content-Type: application/json' -X GET '${statusURL}/${jobID}/stdout/?format=txt_download'
                echo "********************************************************************************\nComplete : Ansible Job Output\n********************************************************************************\n"

                echo "Ansible job status : \$jobStatus"
                if [ "\$jobStatus" == "failed" ]
                then
                    exit 1
                fi
            """, returnStdout: true)

            if (ansibleStdout.contains("skipping: no hosts matched")) {
                println("Error: Ansible playbook reported 'no hosts matched'. JobId: ${jobID}")
                error("Ansible playbook reported 'no hosts matched'. JobId: ${jobID}")
            }

            return ansibleStdout;
        }
    }
}
def getApproval(String message, String approvers) {
    userAborted = false
        startMillis = System.currentTimeMillis()
        timeoutMillis = 180000
        def jobName = currentBuild.fullDisplayName
        approverMailIds = ""
    groupParam = "users"
        groupPath = BUILD_URL.substring(0, BUILD_URL.lastIndexOf("/job"))
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'JENKINS_CREDENTIAL', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME']]) {
        approverIDs = sh(script: """
                  { set +x; } 2>/dev/null;
                    echo `curl -sS --max-time 600 -X GET -u "\$USERNAME:\$USERPASS" ${groupPath}/groups/${approvers}/api/json?tree=${groupParam}`
                    """, returnStdout: true)

            if (approverIDs.contains("HTTP ERROR 401")) {
                println "Problem accessing " + groupPath + "/groups/Nexus-Approvers. Please verify access to jenkins groups."
                sh """
                    { set +x; } 2>/dev/null
                    exit 1
              """
            }
            approverIDs = sh(script: """
                    { set +x; } 2>/dev/null;
            echo `curl -sS --max-time 600 -X GET -u "\$USERNAME:\$USERPASS" ${groupPath}/groups/${approvers}/api/json?tree=${groupParam} | ${WORKSPACE}/jq -r ".${groupParam}"`
                """, returnStdout: true)

            while ((!approverIDs.trim() || approverIDs.contains("HTTP ERROR 404")) && groupPath.contains("job")) {
                groupPath = groupPath.substring(0, groupPath.lastIndexOf("/job"))
                approverIDs = sh(script: """
                    { set +x; } 2>/dev/null
            echo `curl -sS --max-time 600 -X GET -u "\$USERNAME:\$USERPASS" ${groupPath}/groups/${approvers}/api/json?tree=${groupParam} | ${WORKSPACE}/jq -r ".${groupParam}"`
                """, returnStdout: true)
        }
            if (!approverIDs.trim()) {
                sh """
            { set +x; } 2>/dev/null;
            echo "${approvers} group not found."
            exit 1
        """
            }
        }
        Eval.me(approverIDs).each {
            approverMailIds = approverMailIds + "," + User.get(it).getProperty(Mailer.UserProperty.class).getAddress()
        }
        approverIDs = approverIDs.replaceAll("\\\"", "").replaceAll("\\[", "").replaceAll("\\]", "").trim()
        approverMailIds = approverMailIds.replaceFirst(",", "")
       try {
        println "********************************************************************************\n${message}\n********************************************************************************"
        emailext body: '''${SCRIPT, template="Mail-HTML.template"}''',
                mimeType: 'text/html',
                subject: "[Jenkins] ${currentBuild.fullDisplayName} is awaiting approval",
                to: "${approverMailIds}"
           
         timeout(time: 5, unit: 'MINUTES') {
            input(id: 'userInput', message: 'Would you like to perform the operation?')  
        }
    } catch (Exception ex) {
           sh(script: """exit 1""", returnStdout: true)
  }
}
                def getFolderName() {
                              def array = pwd().split("/")
                              return array[array.length - 2];
                             }
return this;
