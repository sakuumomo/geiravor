use crate::status::Status;

#[derive(Debug, Clone)]
pub enum NowPlayingEvent {
    Snapshot(Status),
    IcyTitle(String),
    StreamError,
}

#[derive(Debug, Clone, Default)]
pub struct NowPlayingState {
    pub status: Option<Status>,
    pub local_at_fetch: i64,
    pub refetch: bool,
    pub stream_down: bool,
    last_icy: Option<String>,
}

impl NowPlayingState {
    pub fn apply(&mut self, event: NowPlayingEvent, local_now: i64) {
        match event {
            NowPlayingEvent::Snapshot(status) => {
                self.status = Some(status);
                self.local_at_fetch = local_now;
                self.refetch = false;
                self.stream_down = false;
                self.last_icy = None;
            }
            NowPlayingEvent::IcyTitle(title) => {
                if self.last_icy.as_ref() == Some(&title) {
                    return;
                }
                self.last_icy = Some(title.clone());
                if let Some(status) = &self.status {
                    if status.np != title {
                        self.refetch = true;
                    }
                }
            }
            NowPlayingEvent::StreamError => {
                self.stream_down = true;
            }
        }
    }
}
