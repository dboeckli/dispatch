cd target/helm/repo

$file = Get-ChildItem -Filter dispatch-chart-*.tgz | Select-Object -First 1
$APPLICATION_NAME = Get-ChildItem -Directory | Where-Object { $_.LastWriteTime -ge $file.LastWriteTime } | Select-Object -ExpandProperty Name

helm uninstall $APPLICATION_NAME --namespace dispatch

kubectl delete pod -n dispatch --field-selector=status.phase==Succeeded
kubectl delete pod -n dispatch --field-selector=status.phase==Failed
kubectl delete namespace dispatch
