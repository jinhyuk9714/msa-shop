{{- define "msa-shop.secretName" -}}
{{- .Release.Name }}-secrets
{{- end -}}

{{- define "msa-shop.mysqlHost" -}}
{{- .Release.Name }}-mysql
{{- end -}}

{{- define "msa-shop.rabbitmqHost" -}}
{{- .Release.Name }}-rabbitmq
{{- end -}}

{{- define "msa-shop.zipkinUrl" -}}
http://{{ .Release.Name }}-zipkin:9411/api/v2/spans
{{- end -}}
