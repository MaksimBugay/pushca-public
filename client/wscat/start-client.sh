#!/bin/bash
WORKSPACE_ID="workSpaceMain"
ACCOUNT_ID="client$1@test.ee"
DEVICE_ID="311aae05-bade-48bf-b390-47a93a66c89e"
APPLICATION_ID="MY_JAVA_HEADLESS"

REQUEST_BODY=$( jq -n \
                  --arg workSpaceId "$WORKSPACE_ID" \
                  --arg accountId "$ACCOUNT_ID" \
                  --arg deviceId "$DEVICE_ID" \
                  --arg applicationId "$APPLICATION_ID" \
                  '{client:{workSpaceId: $workSpaceId, accountId: $accountId, deviceId: $deviceId, applicationId: $applicationId}}' )

echo "${REQUEST_BODY}"

wsurl=$(curl -s http://127.0.0.1:8050/open-connection \
  -H 'Content-Type: application/json' \
  -d "$REQUEST_BODY" | jq '.externalAdvertisedUrl')

echo "${wsurl:1: -1}"

wscat -c "${wsurl:1: -1}"

read -p "Press any key..."