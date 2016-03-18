package main

import (
    "flag"
    "fmt"
    "io"
    "log"
    "net"
    "os"
    //"syscall"
    "time"

    "golang.org/x/crypto/ssh"
    "golang.org/x/crypto/ssh/agent"

    "github.com/pkg/sftp"
)

var (
    USER = flag.String("user", os.Getenv("USER"), "ssh username")
    HOST = flag.String("host", "localhost", "ssh server hostname")
    PORT = flag.Int("port", 22, "ssh server port")
    PASS = flag.String("pass", os.Getenv("SOCKSIE_SSH_PASSWORD"), "ssh password")
    SIZE = flag.Int("s", 1<<15, "set max packet size")
)

func init() {
    flag.Parse()
}

func main() {
    var auths []ssh.AuthMethod
    if aconn, err := net.Dial("unix", os.Getenv("SSH_AUTH_SOCK")); err == nil {
        auths = append(auths, ssh.PublicKeysCallback(agent.NewClient(aconn).Signers))
    }
    if *PASS != "" {
        auths = append(auths, ssh.Password(*PASS))
    }

    config := ssh.ClientConfig{
        User: *USER,
        Auth: auths,
    }
    addr := fmt.Sprintf("%s:%d", *HOST, *PORT)
    conn, err := ssh.Dial("tcp", addr, &config)
    if err != nil {
        log.Fatalf("unable to connect to [%s]: %v", addr, err)
    }
    defer conn.Close()

    c, err := sftp.NewClient(conn, sftp.MaxPacket(*SIZE))
    if err != nil {
        log.Fatalf("unable to start sftp subsystem: %v", err)
    }
    defer c.Close()

    w, err := c.Create("/home/vagrant/test_file_go")
    //w, err := c.OpenFile("/home/vagrant/test_file_go", syscall.O_WRONLY)
    if err != nil {
        log.Fatalf("Remote %s", err)
    }
    defer w.Close()

    f, err := os.Open("test_file")
    if err != nil {
        log.Fatalf("Local %s", err)
    }
    defer f.Close()

    const size int64 = 1<<20 * 1e3

    //log.Printf("writing %v bytes", size)
    t1 := time.Now()
    n, err := io.Copy(w, io.LimitReader(f, size))
    if err != nil {
        log.Fatal(err)
    }
    if n != size {
        log.Fatalf("copy: expected %v bytes, got %d", size, n)
    }
    log.Printf("Go wrote %v bytes in %s", size, time.Since(t1))
}
