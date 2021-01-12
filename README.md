# Pop3Receiver

We can use this program to receiver kindle push email, an auto reply the confirm email without human action.

# Intro
An Auto reply amazon.com's email service.

# How to use?
At command line,use

```
java -jar Pop3Receiver.jar
```

parameter like 
```
Usage: Pop3Receiver <server[:port]> <username> <password|-|*|VARNAME> [TLS [true=implicit]]
```

Example:

```
Pop3Receiver pop.163.com:995 username password TLS
```