uniffi::setup_scaffolding!();

/// UniFFI smoke test. Real `/api` types land in a later task.
#[uniffi::export]
pub fn add(left: u32, right: u32) -> u32 {
    left.saturating_add(right)
}

#[cfg(test)]
mod tests {
    use super::add;

    #[test]
    fn add_ones() {
        assert_eq!(add(1, 2), 3);
    }
}
