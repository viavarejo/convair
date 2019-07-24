interface ConvairStage {
    Closure shouldRun
    Closure run
}


def call(Closure body){
    def parameters = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = parameters

    body()

    Map variables = parameters.variables ?: [:]
    variables.each {
        env[it.key] = it.value
    }
    Map<String, ConvairStage> selectedStages = parameters.selectedStages

    pipeline {
        agent {
            node {
                label parameters.selectedAgent
            }
        }
        stages {
            stage("Initialize"){
                steps{
                    script {
                        println parameters
                        sh "env"
                    }
                }
            }

            stage("Dynamic Stages"){

                steps{
                    script {
                        def scriptClosure = owner
                        selectedStages.each { myStage ->
                            myStage.value.shouldRun.delegate = scriptClosure
                            myStage.value.run.delegate = scriptClosure
                            myStage.value.shouldRun.resolveStrategy = Closure.DELEGATE_FIRST
                            myStage.value.run.resolveStrategy = Closure.DELEGATE_FIRST
                            stage(myStage.key){
                                if(myStage.value.shouldRun()){
                                    myStage.value.run()
                                } else {
                                    println "Stage ${myStage.key} skipped"
                                }
                            }
                        }
                    }
                }
            }

        }
    }

}
