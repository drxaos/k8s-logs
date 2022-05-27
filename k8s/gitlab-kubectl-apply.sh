#!/bin/bash
# gitlab-ci не дружит с символом двоеточия, поэтому скрипт подстановки параметров вынесен отдельно

echo "PROD_VERSION: $PROD_VERSION"
echo "PROD_NAMESPACE: $PROD_NAMESPACE"
echo "PROD_LABEL: $PROD_LABEL"
echo "PROD_REPLICAS: $PROD_REPLICAS"

# подменяем переменные и выкатываем
sed "s/[^:]*\#\$VERSION/$PROD_VERSION/g" k8s/deploy.yaml | \
  sed "s/[^:]*\#\$NAMESPACE/ $PROD_NAMESPACE/g" | \
  sed "s/[^:]*\#\$LABEL/ $PROD_LABEL/g" | \
  sed "s/[^:]*\#\$REPLICAS/ $PROD_REPLICAS/g" > k8s/gitlab-deploy.yaml

echo "--- start k8s/gitlab-deploy.yaml"
cat k8s/gitlab-deploy.yaml
echo "--- end k8s/gitlab-deploy.yaml"

cat .kube/config | grep current-context

# ждем пока все выкатится
kubectl apply -f k8s/gitlab-deploy.yaml && \
kubectl get deploy -o name --namespace $PROD_NAMESPACE | \
  xargs -n1 -t kubectl rollout status --namespace $PROD_NAMESPACE
