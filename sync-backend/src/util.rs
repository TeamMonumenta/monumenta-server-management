use std::{
    collections::HashMap,
    hash::{Hash, Hasher},
    ops::Deref,
    sync::{
        Arc,
        atomic::{AtomicU64, Ordering},
        nonpoison::Mutex,
    },
    time::Duration,
};

use anyhow::Result;
use log::{error, info};
use tokio::{
    select,
    signal::unix::{SignalKind, signal},
    sync::oneshot,
    time,
};
use tokio_util::{sync::CancellationToken, task::TaskTracker};

use crate::events::{Event, EventBusSender};

#[repr(transparent)]
pub struct ArcId<T>(Arc<T>);

impl<T> Clone for ArcId<T> {
    fn clone(&self) -> Self {
        Self(self.0.clone())
    }
}

impl<T> ArcId<T> {
    pub fn new(value: T) -> Self {
        ArcId(Arc::new(value))
    }

    pub fn get_arc(&self) -> &Arc<T> {
        &self.0
    }
}

impl<T> Deref for ArcId<T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        self.0.deref()
    }
}

impl<T> PartialEq for ArcId<T> {
    fn eq(&self, other: &Self) -> bool {
        Arc::ptr_eq(&self.0, &other.0)
    }
}

impl<T> Eq for ArcId<T> {}

impl<T> Hash for ArcId<T> {
    fn hash<H: Hasher>(&self, state: &mut H) {
        (Arc::as_ptr(&self.0) as usize).hash(state);
    }
}

pub fn poll_signals(bus: EventBusSender, cancel_ctx: CancelContext) -> Result<()> {
    let mut sigint_stream = signal(SignalKind::interrupt())?;
    let mut sighup_stream = signal(SignalKind::hangup())?;

    cancel_ctx.tracker.spawn(async move {
        loop {
            select! {
                biased;

                _ = cancel_ctx.token.cancelled() => break,
                _ = sigint_stream.recv() => {
                    info!("got SIGINT, exiting server...");
                    if let Err(err) = bus.send(Event::StopServer) {
                        error!("failed to send Event::StopServer to bus: {err}");
                        break;
                    }
                },
                _ = sighup_stream.recv() => {
                    info!("got SIGHUP, reloading config...");
                    if let Err(err) = bus.send(Event::ReloadConfig) {
                        error!("failed to send Event::ReloadConfig to bus: {err}");
                        break;
                    }
                },
            }
        }
    });

    Ok(())
}

#[derive(Clone)]
pub struct CancelContext {
    pub token: CancellationToken,
    pub tracker: TaskTracker,
}

impl CancelContext {
    pub fn new() -> Self {
        CancelContext {
            token: CancellationToken::new(),
            tracker: TaskTracker::new(),
        }
    }

    pub fn token_copy(&self) -> CancellationToken {
        self.token.clone()
    }

    pub async fn stop(&self) {
        self.token.cancel();
        self.tracker.close();
        self.tracker.wait().await;
    }
}

#[derive(Debug)]
pub struct TimeoutError;

struct SequenceTrackerInner<T> {
    next_seq: AtomicU64,
    cancel_ctx: CancelContext,
    pending: Mutex<HashMap<u64, oneshot::Sender<T>>>,
}

#[derive(Clone)]
pub struct SequenceTracker<T> {
    inner: Arc<SequenceTrackerInner<T>>,
}

impl<T> SequenceTracker<T>
where
    T: Send + 'static,
{
    pub fn new(cancel_ctx: CancelContext) -> Self {
        Self {
            inner: Arc::new(SequenceTrackerInner {
                cancel_ctx,
                next_seq: AtomicU64::new(0),
                pending: Mutex::new(HashMap::new()),
            }),
        }
    }

    pub async fn submit<F>(&self, action: F, timeout: Duration) -> Result<T, TimeoutError>
    where
        F: FnOnce(u64) + Send + 'static,
    {
        let seq = self.inner.next_seq.fetch_add(1, Ordering::Relaxed);

        let (tx, rx) = oneshot::channel();

        {
            let mut pending = self.inner.pending.lock();
            pending.insert(seq, tx);
        }

        // fire the action *after* registering
        action(seq);

        // wait for completion or timeout
        let res = select! {
           biased;

            // clean up on timeout
           _ =  self.inner.cancel_ctx.token.cancelled() => {
               Err(TimeoutError)
           }
          _ = time::sleep(timeout) => {
               let mut pending = self.inner.pending.lock();
               pending.remove(&seq);
               Err(TimeoutError)
           }
           res = rx => {
               match res {
                   Ok(value) => Ok(value),
                   Err(_) => Err(TimeoutError), // sender dropped
               }
           }
        };

        let mut pending = self.inner.pending.lock();
        pending.remove(&seq);

        res
    }

    pub fn complete(&self, seq: u64, value: T) {
        let sender = {
            let mut pending = self.inner.pending.lock();
            pending.remove(&seq)
        };

        if let Some(tx) = sender {
            let _ = tx.send(value); // ignore if receiver dropped
        }
    }
}
