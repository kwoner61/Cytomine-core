package be.cytomine.command

class TimeoutForAPIRequestService {

    long counterSleep
    long limitCounter
    long timeToWait

    TimeoutForAPIRequestService()
    {
    }
    TimeoutForAPIRequestService(long limitCounter,long timeToWait)
    {
        this.limitCounter=limitCounter
        this.timeToWait=timeToWait
    }
    void startCounterTimeout()
    {
        this.counterSleep=0
    }
    void sleep()
    {
        sleep(this.timeToWait)
    }
    void info()
    {
        log.info("Attempt: ${counterSleep+1} Limit of attempts: ${limitCounter}")
    }
    void incrementCounter()
    {
        (this.counterSleep)++
    }
}
