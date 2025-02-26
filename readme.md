curl -i -s -X GET https://ice.it.global.hsbc/ice/api/v1/changes/CHG4625344 \
  -H "Authorization: Basic $(echo -n 'service-account:YourActualPassword' | base64)"

