package com.jerrychau.xremote

open class BaseXRemoteException(message: String) : Exception(message)

class ConnectionFailureException(message: String?) : BaseXRemoteException("Failed to connect to remote device: $message")

class InvalidTokenException() : BaseXRemoteException("Invalid token.")

class RemoteException(reasonFromRemote: String) : BaseXRemoteException("Remote device returned an error: $reasonFromRemote")