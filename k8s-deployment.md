# OpenAS2 Kubernetes Deployment Guide

## Overview
This guide provides a step-by-step approach to deploying [OpenAS2](https://github.com/OpenAS2/OpenAs2App) in a Kubernetes (K8s) cluster. It covers building Docker images, creating Kubernetes resources, and setting up configurations.

## Prerequisites
Ensure you have the following installed and configured:

- **Docker** with Buildx enabled
- **Kubernetes (K8s) Cluster** (Minikube, Docker Desktop, or a Cloud Provider)
- **kubectl** (Kubernetes CLI)

## Clone the Repository

```sh
git clone git@github.com:OpenAS2/OpenAs2App.git
```

## Build and Push Docker Images
Replace `myrepo` with your Docker Hub or private registry repository:

```sh
docker buildx build --platform linux/amd64,linux/arm64 --tag myrepo/openas2app:latest --push .
docker buildx build --platform linux/amd64,linux/arm64 --file Dockerfile_WebUI --tag myrepo/openas2ui:latest --push .
```

## Deploy OpenAS2 to Kubernetes

### 1. Create a Namespace
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: openas2-ns
```

### 2. Create Configuration (ConfigMap)
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: openas2-config
  namespace: openas2-ns
data:
  OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__BASEURI: "http://0.0.0.0:8080"
  OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__ENABLED: "true"
```

### 3. Deploy OpenAS2 Application
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: openas2
  namespace: openas2-ns
spec:
  replicas: 1
  selector:
    matchLabels:
      app: openas2
  template:
    metadata:
      labels:
        app: openas2
    spec:
      containers:
        - name: openas2
          image: myrepo/openas2app:latest
          ports:
            - containerPort: 10080
            - containerPort: 10081
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: openas2-config
          env:
            - name: OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__USERID
              valueFrom:
                secretKeyRef:
                  name: openas2-secret
                  key: OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__USERID
            - name: OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__PASSWORD
              valueFrom:
                secretKeyRef:
                  name: openas2-secret
                  key: OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__PASSWORD
          volumeMounts:
            - name: config-volume
              mountPath: /opt/openas2/config
            - name: data-volume
              mountPath: /opt/openas2/data
      volumes:
        - name: config-volume
          persistentVolumeClaim:
            claimName: openas2-config-pvc
        - name: data-volume
          persistentVolumeClaim:
            claimName: openas2-data-pvc
```

### 4. Create Persistent Volume Claims
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: openas2-config-pvc
  namespace: openas2-ns
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: openas2-data-pvc
  namespace: openas2-ns
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

### 5. Create Kubernetes Secret (Base64 Encoded Credentials)
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: openas2-secret
  namespace: openas2-ns
type: Opaque
data:
  OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__USERID: "dXNlcklE"  # Base64 encoded 'userID'
  OPENAS2PROP_RESTAPI__COMMAND__PROCESSOR__PASSWORD: "cFdk"   # Base64 encoded 'pWd'
```

### 6. Create OpenAS2 Service
```yaml
apiVersion: v1
kind: Service
metadata:
  name: openas2-service
  namespace: openas2-ns
spec:
  selector:
    app: openas2
  ports:
    - name: web-port
      protocol: TCP
      port: 4080
      targetPort: 10080
    - name: secure-web-port
      protocol: TCP
      port: 4081
      targetPort: 10081
    - name: api-port
      protocol: TCP
      port: 8443
      targetPort: 8080
  type: ClusterIP
```

## Access OpenAS2 WebUI

```sh
kubectl port-forward svc/openas2-service 9443:8443 -n openas2-ns  # for Exposing api 
kubectl port-forward svc/openas2-webui-service 8080:8080 -n openas2-ns
```

Access the services via:

- API: [http://localhost:9443/api](http://localhost:9443/api)
- WebUI: [http://localhost:8080/#/](http://localhost:8080/#/)
