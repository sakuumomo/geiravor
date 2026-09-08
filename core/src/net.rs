//! Blocking HTTP + in-flight coalescer. Parsers do not live here.

use std::collections::HashMap;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;

use crate::USER_AGENT;
use crate::error::ApiError;

/// GET / CSRF POST / form POST. No add-fave.
pub trait HttpClient: Send + Sync {
    fn get(&self, url: &str) -> Result<Vec<u8>, ApiError>;
    fn post_csrf(&self, url: &str, token: &str, form: &[(&str, &str)])
    -> Result<Vec<u8>, ApiError>;
    fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<Vec<u8>, ApiError>;
    /// POST with CSRF; returns status + body so 403 can retry.
    fn post_csrf_raw(
        &self,
        url: &str,
        token: &str,
        form: &[(&str, &str)],
    ) -> Result<(u16, Vec<u8>), ApiError>;
}

/// Process-wide blocking reqwest + rustls + cookie jar.
pub struct ReqwestClient {
    inner: reqwest::blocking::Client,
}

impl ReqwestClient {
    /// Build the shared client. Cookie jar is required for CSRF.
    pub fn new() -> Result<Self, ApiError> {
        let inner = reqwest::blocking::Client::builder()
            .user_agent(USER_AGENT)
            .cookie_store(true)
            .timeout(Duration::from_secs(20))
            .connect_timeout(Duration::from_secs(10))
            .build()
            .map_err(|e| ApiError::Network {
                detail: e.to_string(),
            })?;
        Ok(Self { inner })
    }
}

impl HttpClient for ReqwestClient {
    fn get(&self, url: &str) -> Result<Vec<u8>, ApiError> {
        tracing::debug!(url, "GET");
        let res = self.inner.get(url).send().map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        let code = res.status().as_u16();
        if !res.status().is_success() {
            return Err(ApiError::Http { code });
        }
        let bytes = res.bytes().map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        crate::parse::check_bound(&bytes)?;
        Ok(bytes.to_vec())
    }

    fn post_csrf(
        &self,
        url: &str,
        token: &str,
        form: &[(&str, &str)],
    ) -> Result<Vec<u8>, ApiError> {
        let (code, bytes) = self.post_csrf_raw(url, token, form)?;
        if !(200..300).contains(&code) {
            return Err(ApiError::Http { code });
        }
        Ok(bytes)
    }

    fn post_csrf_raw(
        &self,
        url: &str,
        token: &str,
        form: &[(&str, &str)],
    ) -> Result<(u16, Vec<u8>), ApiError> {
        tracing::debug!(url, "POST csrf");
        let res = self
            .inner
            .post(url)
            .header("X-CSRF-Token", token)
            .form(form)
            .send()
            .map_err(|e| ApiError::Network {
                detail: e.to_string(),
            })?;
        let code = res.status().as_u16();
        let bytes = res.bytes().map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        crate::parse::check_bound(&bytes)?;
        Ok((code, bytes.to_vec()))
    }

    fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<Vec<u8>, ApiError> {
        tracing::debug!(url, "POST form");
        let res = self
            .inner
            .post(url)
            .form(form)
            .send()
            .map_err(|e| ApiError::Network {
                detail: e.to_string(),
            })?;
        let code = res.status().as_u16();
        if !res.status().is_success() {
            return Err(ApiError::Http { code });
        }
        let bytes = res.bytes().map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        crate::parse::check_bound(&bytes)?;
        Ok(bytes.to_vec())
    }
}

type Slot = Arc<(Mutex<Option<Result<Vec<u8>, ApiError>>>, Condvar)>;

/// One in-flight GET per URL.
pub struct Coalescer<C: HttpClient> {
    inner: C,
    inflight: Mutex<HashMap<String, Slot>>,
}

impl<C: HttpClient> Coalescer<C> {
    pub fn new(inner: C) -> Self {
        Self {
            inner,
            inflight: Mutex::new(HashMap::new()),
        }
    }

    pub fn get(&self, url: &str) -> Result<Vec<u8>, ApiError> {
        let mut map = self.inflight.lock().expect("coalesce");
        if let Some(slot) = map.get(url).cloned() {
            drop(map);
            let (lock, cv) = &*slot;
            let mut g = lock.lock().expect("coalesce wait");
            while g.is_none() {
                g = cv.wait(g).expect("coalesce wait");
            }
            return g.as_ref().expect("filled").clone();
        }
        let slot: Slot = Arc::new((Mutex::new(None), Condvar::new()));
        map.insert(url.to_string(), slot.clone());
        drop(map);

        let result = self.inner.get(url);

        let (lock, cv) = &*slot;
        *lock.lock().expect("coalesce fill") = Some(result.clone());
        cv.notify_all();
        self.inflight.lock().expect("coalesce").remove(url);
        result
    }

    pub fn post_csrf(
        &self,
        url: &str,
        token: &str,
        form: &[(&str, &str)],
    ) -> Result<Vec<u8>, ApiError> {
        self.inner.post_csrf(url, token, form)
    }

    pub fn post_csrf_raw(
        &self,
        url: &str,
        token: &str,
        form: &[(&str, &str)],
    ) -> Result<(u16, Vec<u8>), ApiError> {
        self.inner.post_csrf_raw(url, token, form)
    }

    pub fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<Vec<u8>, ApiError> {
        self.inner.post_form(url, form)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU32, Ordering};
    use std::thread;
    use std::time::Duration;

    struct SlowOnce {
        hits: AtomicU32,
    }

    impl HttpClient for SlowOnce {
        fn get(&self, _url: &str) -> Result<Vec<u8>, ApiError> {
            self.hits.fetch_add(1, Ordering::SeqCst);
            thread::sleep(Duration::from_millis(50));
            Ok(b"ok".to_vec())
        }
        fn post_csrf(&self, _: &str, _: &str, _: &[(&str, &str)]) -> Result<Vec<u8>, ApiError> {
            unimplemented!()
        }
        fn post_form(&self, _: &str, _: &[(&str, &str)]) -> Result<Vec<u8>, ApiError> {
            unimplemented!()
        }
        fn post_csrf_raw(
            &self,
            _: &str,
            _: &str,
            _: &[(&str, &str)],
        ) -> Result<(u16, Vec<u8>), ApiError> {
            unimplemented!()
        }
    }

    #[test]
    fn coalesce_one_get() {
        let c = Arc::new(Coalescer::new(SlowOnce {
            hits: AtomicU32::new(0),
        }));
        let a = {
            let c = c.clone();
            thread::spawn(move || c.get("https://r-a-d.io/api"))
        };
        let b = {
            let c = c.clone();
            thread::spawn(move || c.get("https://r-a-d.io/api"))
        };
        assert_eq!(a.join().unwrap().unwrap(), b"ok");
        assert_eq!(b.join().unwrap().unwrap(), b"ok");
        assert_eq!(c.inner.hits.load(Ordering::SeqCst), 1);
    }
}
