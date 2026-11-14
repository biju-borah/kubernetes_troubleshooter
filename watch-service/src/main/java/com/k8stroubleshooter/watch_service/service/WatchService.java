package com.k8stroubleshooter.watch_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WatchService implements ApplicationRunner {
    private final PodWatcherService podWatcherService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        podWatcherService.monitorPod();
    }
}
