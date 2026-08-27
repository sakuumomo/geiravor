use std::time::Duration;

use super::IrcError;

pub trait IrcIo {
    fn send(&mut self, line: &str) -> Result<(), IrcError>;
    fn recv(&mut self) -> Result<String, IrcError>;
    fn set_read_timeout(&mut self, _timeout: Duration) -> Result<(), IrcError> {
        Ok(())
    }
}

#[cfg(test)]
use std::collections::VecDeque;

#[cfg(test)]
pub struct ScriptedIo {
    pub sent: Vec<String>,
    incoming: VecDeque<String>,
}

#[cfg(test)]
impl ScriptedIo {
    pub fn new(incoming: Vec<String>) -> Self {
        Self {
            sent: Vec::new(),
            incoming: incoming.into(),
        }
    }
}

#[cfg(test)]
impl IrcIo for ScriptedIo {
    fn send(&mut self, line: &str) -> Result<(), IrcError> {
        self.sent.push(line.to_string());
        Ok(())
    }

    fn recv(&mut self) -> Result<String, IrcError> {
        self.incoming.pop_front().ok_or_else(|| IrcError::Timeout {
            detail: "no more scripted lines".into(),
        })
    }
}
