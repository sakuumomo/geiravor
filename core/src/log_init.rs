//! `tracing` → logcat tag `geiravor`. Host tests are a no-op.

/// Debug APK: DEBUG. Release: INFO. Never log secrets.
#[uniffi::export]
pub fn init_logging(debug: bool) {
    #[cfg(target_os = "android")]
    {
        let max = if debug {
            log::LevelFilter::Debug
        } else {
            log::LevelFilter::Info
        };
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(max)
                .with_tag("geiravor"),
        );
        use tracing_subscriber::prelude::*;
        let filter = if debug {
            tracing_subscriber::filter::LevelFilter::DEBUG
        } else {
            tracing_subscriber::filter::LevelFilter::INFO
        };
        let _ = tracing_subscriber::registry()
            .with(AndroidLogLayer)
            .with(filter)
            .try_init();
        tracing::info!("logging on");
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = debug;
    }
}

#[cfg(target_os = "android")]
struct AndroidLogLayer;

#[cfg(target_os = "android")]
impl<S: tracing::Subscriber> tracing_subscriber::Layer<S> for AndroidLogLayer {
    fn on_event(
        &self,
        event: &tracing::Event<'_>,
        _ctx: tracing_subscriber::layer::Context<'_, S>,
    ) {
        let mut msg = String::new();
        event.record(&mut Msg(&mut msg));
        let level = match *event.metadata().level() {
            tracing::Level::ERROR => log::Level::Error,
            tracing::Level::WARN => log::Level::Warn,
            tracing::Level::INFO => log::Level::Info,
            tracing::Level::DEBUG => log::Level::Debug,
            tracing::Level::TRACE => log::Level::Trace,
        };
        log::log!(level, "{msg}");
    }
}

#[cfg(target_os = "android")]
struct Msg<'a>(&'a mut String);

#[cfg(target_os = "android")]
impl tracing::field::Visit for Msg<'_> {
    fn record_debug(&mut self, field: &tracing::field::Field, value: &dyn std::fmt::Debug) {
        if field.name() == "message" {
            use std::fmt::Write;
            let _ = write!(self.0, "{value:?}");
            if self.0.starts_with('"') && self.0.ends_with('"') && self.0.len() >= 2 {
                let inner = self.0[1..self.0.len() - 1].to_string();
                *self.0 = inner;
            }
        }
    }
}
