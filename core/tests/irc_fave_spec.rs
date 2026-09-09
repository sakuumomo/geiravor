//! Local TLS IRC. Does not open irc.rizon.net.

use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use geiravor_core::{
    FaveConfig, FaveKind, IrcProfile, RadioCore, TapSnapshot, connect_irc, run_add_fave, run_probe,
};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer};
use rustls::{ServerConfig, ServerConnection, StreamOwned};

fn install_ring() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

fn self_signed() -> (CertificateDer<'static>, PrivateKeyDer<'static>) {
    let ck = rcgen::generate_simple_self_signed(vec!["127.0.0.1".into(), "localhost".into()])
        .expect("cert");
    let cert = CertificateDer::from(ck.cert.der().to_vec());
    let key = PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(ck.signing_key.serialize_der()));
    (cert, key)
}

fn serve_script(script: Vec<(bool, String)>) -> (u16, thread::JoinHandle<Vec<String>>) {
    install_ring();
    let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
    let port = listener.local_addr().expect("addr").port();
    let (cert, key) = self_signed();
    let config = Arc::new(
        ServerConfig::builder()
            .with_no_client_auth()
            .with_single_cert(vec![cert], key)
            .expect("server cert"),
    );
    let handle = thread::spawn(move || {
        let (tcp, _) = listener.accept().expect("accept");
        tcp.set_read_timeout(Some(Duration::from_secs(5))).ok();
        let conn = ServerConnection::new(config).expect("server conn");
        let mut tls = StreamOwned::new(conn, tcp);
        let mut got = Vec::new();
        let mut buf = String::new();
        let mut reader = BufReader::new(&mut tls);
        for (expect_read, outgoing) in script {
            if expect_read {
                buf.clear();
                if reader.read_line(&mut buf).ok().unwrap_or(0) == 0 {
                    break;
                }
                got.push(buf.trim_end().to_string());
            }
            if !outgoing.is_empty() {
                let stream = reader.get_mut();
                let _ = stream.write_all(outgoing.as_bytes());
                let _ = stream.write_all(b"\r\n");
                let _ = stream.flush();
            }
        }
        while let Ok(n) = {
            buf.clear();
            reader.read_line(&mut buf)
        } {
            if n == 0 {
                break;
            }
            got.push(buf.trim_end().to_string());
        }
        got
    });
    (port, handle)
}

fn afk_config(insecure: bool, port: u16) -> FaveConfig {
    FaveConfig {
        nick: "Geiravor".into(),
        list_nick: String::new(),
        profile: IrcProfile::Bouncer,
        nickserv_password: String::new(),
        bouncer_host: "127.0.0.1".into(),
        bouncer_port: port,
        bouncer_pass: String::new(),
        allow_insecure_tls: insecure,
        sasl_username: String::new(),
        sasl_password: String::new(),
        client_cert_pem: String::new(),
        client_key_pem: String::new(),
        tls_fingerprint: String::new(),
    }
}

#[test]
fn self_signed_fails_unless_insecure_toggle() {
    let (port, server) = serve_script(vec![(false, String::new())]);
    let err = connect_irc("127.0.0.1", port, false, "", "", "");
    assert!(err.is_err(), "verified TLS must reject self-signed");
    let _ = server.join();
}

#[test]
fn insecure_handshake_reports_server_fingerprint() {
    let (port, server) = serve_script(vec![(false, String::new())]);
    let conn = connect_irc("127.0.0.1", port, true, "", "", "").expect("handshake");
    assert!(
        conn.server_fingerprint().contains(':'),
        "got {}",
        conn.server_fingerprint()
    );
    drop(conn);
    let _ = server.join();
}

#[test]
fn fingerprint_pin_rejects_mismatch_even_when_insecure() {
    let (port, server) = serve_script(vec![(false, String::new())]);
    let err = match connect_irc("127.0.0.1", port, true, "", "", "00:11:22:33") {
        Ok(_) => panic!("pin should reject"),
        Err(e) => e,
    };
    assert!(err.to_string().contains("fingerprint mismatch"), "{err}");
    let _ = server.join();
}

#[test]
fn bouncer_afk_fave_over_insecure_tls_no_quit() {
    let (port, server) = serve_script(vec![
        (true, String::new()),
        (true, ":irc 001 geiravor-test :welcome".into()),
        (
            true,
            ":Hanyuu-sama!b@r NOTICE x :Added 'Hirasawa Susumu - Gats' to your favorites.".into(),
        ),
    ]);
    let mut conn = connect_irc("127.0.0.1", port, true, "", "", "").expect("insecure tls");
    let tap = TapSnapshot {
        is_afk: true,
        track_id: 42,
        np: "Hirasawa Susumu - Gats".into(),
    };
    let result = run_add_fave(
        &mut conn,
        &afk_config(true, port),
        &tap,
        || "Hirasawa Susumu - Gats".into(),
        "geiravor-test",
        false,
        None,
    )
    .expect("fave");
    let _ = conn.close_notify();
    assert_eq!(result.kind, FaveKind::Success);
    assert_eq!(result.message, "Hirasawa Susumu - Gats");
    let got = server.join().expect("server");
    assert!(got.iter().any(|l| l == "NICK geiravor-test"));
    assert!(got.iter().any(|l| l == "PRIVMSG Hanyuu-sama :.fave 42"));
    assert!(!got.iter().any(|l| l.starts_with("QUIT")));
    assert!(!got.iter().any(|l| l.contains("NickServ")));
}

#[test]
fn bouncer_afk_unfave_id_over_insecure_tls_no_quit() {
    let (port, server) = serve_script(vec![
        (true, String::new()),
        (true, ":irc 001 geiravor-test :welcome".into()),
        (
            true,
            ":Hanyuu-sama!b@r NOTICE x :'Hirasawa Susumu - Gats' is removed from your favorites."
                .into(),
        ),
    ]);
    let mut conn = connect_irc("127.0.0.1", port, true, "", "", "").expect("insecure tls");
    let tap = TapSnapshot {
        is_afk: true,
        track_id: 42,
        np: "Hirasawa Susumu - Gats".into(),
    };
    let result = run_add_fave(
        &mut conn,
        &afk_config(true, port),
        &tap,
        || "Hirasawa Susumu - Gats".into(),
        "geiravor-test",
        true,
        Some(42),
    )
    .expect("unfave");
    let _ = conn.close_notify();
    assert_eq!(result.kind, FaveKind::Success);
    assert!(!result.favorited);
    assert_eq!(result.message, "Hirasawa Susumu - Gats");
    let got = server.join().expect("server");
    assert!(got.iter().any(|l| l == "PRIVMSG Hanyuu-sama :.unfave 42"));
    assert!(!got.iter().any(|l| l.contains(".fave 42")));
    assert!(!got.iter().any(|l| l.starts_with("QUIT")));
}

#[test]
fn probe_is_handshake_only_no_fave() {
    let (port, server) = serve_script(vec![
        (true, String::new()),
        (true, ":irc 001 geiravor-test :welcome".into()),
    ]);
    let mut conn = connect_irc("127.0.0.1", port, true, "", "", "").expect("tls");
    run_probe(&mut conn, &afk_config(true, port), "geiravor-test").expect("probe");
    let fp = conn.server_fingerprint().to_string();
    let _ = conn.close_notify();
    assert!(fp.contains(':'), "got {fp}");
    let got = server.join().expect("server");
    assert!(
        !got.iter()
            .any(|l| l.contains("Hanyuu") || l.contains(".fave"))
    );
    assert!(!got.iter().any(|l| l.starts_with("QUIT")));
}

#[test]
fn probe_empty_nick_fails_without_irc() {
    let dir = std::env::temp_dir().join(format!("geiravor-probe-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
    let mut cfg = afk_config(true, 1);
    cfg.nick.clear();
    let err = core.probe(cfg).expect_err("empty nick");
    assert!(err.to_string().contains("empty nick"), "{err}");
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn empty_nick_is_noop_without_irc() {
    let dir = std::env::temp_dir().join(format!("geiravor-fave-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
    let mut cfg = afk_config(true, 1);
    cfg.nick.clear();
    let r = core.add_fave(cfg, false, 0, String::new(), false, 0);
    assert_eq!(r.kind, FaveKind::Noop);
    let _ = std::fs::remove_dir_all(&dir);
}

fn serve_bouncer_capture() -> (u16, thread::JoinHandle<Vec<String>>) {
    install_ring();
    let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
    let port = listener.local_addr().expect("addr").port();
    let (cert, key) = self_signed();
    let config = Arc::new(
        ServerConfig::builder()
            .with_no_client_auth()
            .with_single_cert(vec![cert], key)
            .expect("server cert"),
    );
    let handle = thread::spawn(move || {
        let (tcp, _) = listener.accept().expect("accept");
        tcp.set_read_timeout(Some(Duration::from_secs(5))).ok();
        let conn = ServerConnection::new(config).expect("server conn");
        let mut tls = StreamOwned::new(conn, tcp);
        let mut got = Vec::new();
        let mut buf = String::new();
        let mut reader = BufReader::new(&mut tls);
        let mut nick = String::from("geiravor-test");
        let mut welcomed = false;
        while let Ok(n) = {
            buf.clear();
            reader.read_line(&mut buf)
        } {
            if n == 0 {
                break;
            }
            let line = buf.trim_end().to_string();
            got.push(line.clone());
            if let Some(rest) = line.strip_prefix("NICK ") {
                nick = rest.trim().to_string();
            }
            if !welcomed && line.starts_with("USER ") {
                welcomed = true;
                let stream = reader.get_mut();
                let _ = stream.write_all(format!(":irc 001 {nick} :welcome\r\n").as_bytes());
                let _ = stream.flush();
            }
            if line.contains("PRIVMSG Hanyuu-sama") {
                let stream = reader.get_mut();
                let _ = stream.write_all(
                    b":Hanyuu-sama!b@r NOTICE x :Added 'Tapped - Song' to your favorites.\r\n",
                );
                let _ = stream.flush();
            }
        }
        got
    });
    (port, handle)
}

#[test]
fn add_fave_uses_tap_snapshot_not_later_domain_np() {
    let dir = std::env::temp_dir().join(format!("geiravor-tap-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
    let json = include_str!("fixtures/api_snapshot.json");
    core.restore_snapshot(json.into()).unwrap();
    assert_eq!(core.snapshot().unwrap().track_id, 15358);
    let (port, server) = serve_bouncer_capture();
    let cfg = afk_config(true, port);
    let r = core.add_fave(cfg, false, 0, "Tapped - Song".into(), true, 42);
    assert_eq!(r.kind, FaveKind::Success, "{r:?}");
    let got = server.join().expect("server");
    assert!(
        got.iter().any(|l| l == "PRIVMSG Hanyuu-sama :.fave 42"),
        "{got:?}"
    );
    assert!(!got.iter().any(|l| l.contains(".fave 15358")), "{got:?}");
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn live_dj_sends_fave_last_when_np_changed() {
    let (port, server) = serve_script(vec![
        (true, String::new()),
        (true, ":irc 001 geiravor-test :welcome".into()),
        (
            true,
            ":Hanyuu-sama!b@r NOTICE x :Added 'Other - Track' to your favorites.".into(),
        ),
        (
            true,
            ":Hanyuu-sama!b@r NOTICE x :'Other - Track' is removed from your favorites.".into(),
        ),
        (
            true,
            ":Hanyuu-sama!b@r NOTICE x :Added 'Intended - Song' to your favorites.".into(),
        ),
    ]);
    let mut conn = connect_irc("127.0.0.1", port, true, "", "", "").expect("tls");
    let tap = TapSnapshot {
        is_afk: false,
        track_id: 99,
        np: "Intended - Song".into(),
    };
    let result = run_add_fave(
        &mut conn,
        &afk_config(true, port),
        &tap,
        || "Other - Track".into(),
        "geiravor-test",
        false,
        None,
    )
    .expect("fave");
    let _ = conn.close_notify();
    let got = server.join().expect("server");
    assert!(got.iter().any(|l| l == "PRIVMSG Hanyuu-sama :.fave last"));
    assert!(got.iter().any(|l| l == "PRIVMSG Hanyuu-sama :.unfave last"));
    assert!(!got.iter().any(|l| l.contains(".fave 99")));
    assert_eq!(result.kind, FaveKind::Failed);
}
